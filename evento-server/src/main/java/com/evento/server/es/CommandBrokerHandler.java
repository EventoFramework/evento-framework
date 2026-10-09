package com.evento.server.es;

import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.exceptions.IdempotencyKeyInProgressException;
import com.evento.common.modeling.exceptions.IdempotencyKeyMismatchException;
import com.evento.common.modeling.messaging.message.application.DecoratedDomainCommandMessage;
import com.evento.common.modeling.messaging.message.application.DomainCommandMessage;
import com.evento.common.modeling.messaging.message.application.DomainCommandResponseMessage;
import com.evento.common.modeling.messaging.message.application.ServiceCommandMessage;
import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.common.utils.LockAcquisitionTimeoutException;
import com.evento.common.utils.PgDistributedLock;
import com.evento.server.bus.NodeAddress;
import com.evento.server.bus.event.BusEvent;
import com.evento.transport.RequestExpiredException;
import com.evento.transport.SendFailedException;
import com.evento.transport.message.Response;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.bus.registry.ClusterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Restores the broker-side command interception that lived in the deleted v1
 * {@code MessageBus}. Registers server-local handlers for every
 * {@code AggregateCommandHandler} and service {@code CommandHandler} that a
 * bundle declares at registration time.
 *
 * <p>Aggregate command flow (per handler invocation):
 * <ol>
 *   <li>Optionally acquire the distributed lock on {@code "RESOURCE:" + lockId}
 *       (only if the command carries a non-null {@code lockId}).</li>
 *   <li>Fetch the aggregate story (snapshot + event stream) from {@link EventStore}.</li>
 *   <li>Wrap the {@code DomainCommandMessage} in a {@code DecoratedDomainCommandMessage}
 *       and forward to the aggregate bundle via {@link BusLifecycle#forward}.</li>
 *   <li>Receive {@code DomainCommandResponseMessage}, persist the domain event
 *       and optional snapshot in {@link EventStore}; call
 *       {@link EventStore#deleteAggregate} if the aggregate was deleted.</li>
 *   <li>Return the stored {@code DomainEventMessage} to the original caller so
 *       that {@code CommandGatewayImpl} can extract the event payload.</li>
 * </ol>
 *
 * <p><b>Caller deadline.</b> Each command carries its caller's deadline
 * ({@link BusLifecycle.DeadlineAwareRequestHandler}). A command whose caller has
 * stopped waiting is not executed: the lock wait is bounded by what is left of the
 * deadline, and the deadline is checked again before the forward and before the
 * append. A caller that timed out used to see a timeout while the broker, queued
 * behind the lock, went on and stored the event anyway (every one of 92 timed-out
 * commands in the lab's saturated run), and a saga retrying the "failed" command
 * applied it twice. A drop answers {@code RequestExpiredException} and logs
 * {@code event=request_expired_dropped}. A command that passes the last check and
 * then outlives its deadline during the append is still indeterminate for the
 * caller; a retry with the same idempotency key resolves it (below).
 *
 * <p><b>Idempotency keys (CLUSTER-READINESS G5).</b> A command may carry an idempotency
 * key ({@code CommandMessage#getIdempotencyKey()}), which is stored with its event, together
 * with the command's identity (payload type and aggregate id), under a partial unique index. A
 * keyed command whose key already has an event <em>of the same identity</em> is answered with
 * that event instead of being forwarded, on whichever server it arrives
 * ({@code event=idempotent_replay}, {@code evento.server.commands.idempotent.replayed}), and
 * no handler runs. The key is looked up in a round trip the command makes anyway (the story
 * fetch under the aggregate lock; the service claim), so a keyed first attempt costs what an
 * unkeyed one does (aggregate) or one statement more (service: the claim). A key recorded for a
 * <em>different</em> command is refused with
 * {@code IdempotencyKeyMismatchException} ({@code event=idempotency_key_mismatch}), never
 * answered with the other command's event. Concurrent duplicates are serialised by the
 * aggregate lock (aggregate commands, key re-checked under it; the unique index backs it up) or
 * by a claim row (service commands, whose handlers have side effects): a duplicate polls the
 * claim without holding any connection, so a keyed service command never needs more than the
 * one lock connection of its own {@code RESOURCE:} lock. A service handler that returns nothing
 * completes its claim, so its retries are answered with an empty result instead of re-running
 * it. A duplicate that stops waiting on another execution of its key is answered
 * {@code IdempotencyKeyInProgressException} (indeterminate), not "expired, not executed"; a
 * deadline drop whose key is found stored becomes the replay. One window is not the broker's to
 * close: a service handler that ran while its event was not stored. The service bundle closes it
 * with a durable outcome store, which answers the retry without running the handler.
 *
 * <p>The {@code before_append} drop applies to aggregate commands only, whose handlers
 * are pure (they compute an event from the story; nothing happened until the broker
 * appends it). A service command handler runs its own side effects: once it has
 * answered, its event is stored whatever the deadline, and a forward that failed after
 * the deadline stays an indeterminate error rather than "not executed". Service
 * commands are still dropped before the lock and before the forward, where nothing ran.
 *
 * <p>Service command flow:
 * <ol>
 *   <li>With an idempotency key: claim the key (a row, see above), or answer from what is
 *       recorded for it.</li>
 *   <li>Optionally acquire the distributed lock on {@code "RESOURCE:" + lockId}
 *       (only if the command carries a non-null {@code lockId}).</li>
 *   <li>Forward the {@code ServiceCommandMessage} unchanged to the service bundle.</li>
 *   <li>If the response is an {@code EventMessage} with a non-null
 *       {@code objectClass}, persist it in {@link EventStore}.</li>
 *   <li>Return the {@code ServiceEventMessage} to the original caller.</li>
 * </ol>
 */
@Component
public class CommandBrokerHandler implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(CommandBrokerHandler.class);
    private static final String RESOURCE_LOCK_PREFIX = "RESOURCE:";
    /** Replay stages (see {@link #idempotentReplayedCount(String)}). */
    public static final String REPLAY_BEFORE_LOCK = "before_lock";
    /** No longer used: aggregate commands are checked with their story ({@link #REPLAY_WITH_STORY}). */
    public static final String REPLAY_AFTER_LOCK = "after_lock";
    /** An aggregate command whose key was found by the story fetch (under the lock, when it has one). */
    public static final String REPLAY_WITH_STORY = "with_story";
    /** No longer used: the claim statement itself reports what is recorded. */
    public static final String REPLAY_AFTER_CLAIM = "after_claim";
    public static final String REPLAY_AFTER_WAIT = "after_wait";
    public static final String REPLAY_AFTER_HANDLER_ERROR = "after_handler_error";
    public static final String REPLAY_APPEND_CONFLICT = "append_conflict";
    public static final String REPLAY_AFTER_EXPIRY = "after_expiry";
    /** Beyond the forward budget, how long a claim outlives a server that died holding it. */
    static final long CLAIM_LEASE_GRACE_MS = 10_000;
    /** Bound on waiting for another execution of a key when the command has no deadline. */
    static final long DEFAULT_KEY_WAIT_MS = 60_000;
    private static final long CLAIM_POLL_INITIAL_MS = 10;
    private static final long CLAIM_POLL_MAX_MS = 200;

    private final AtomicLong idempotentReplays = new AtomicLong();
    private final java.util.concurrent.ConcurrentHashMap<String, AtomicLong> idempotentReplaysByStage =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicLong idempotencyMismatches = new AtomicLong();
    private final AtomicLong idempotencyInProgress = new AtomicLong();
    private final AtomicLong idempotencyUnsupportedWarned = new AtomicLong();
    /** Prefix of every claim owner of this broker instance: one id per process, then a counter. */
    private final String claimOwnerPrefix = java.util.UUID.randomUUID() + ":";
    private final AtomicLong claimOwnerSeq = new AtomicLong();
    private static final long SEND_RETRY_BACKOFF_INITIAL_MS = 50;
    private static final long SEND_RETRY_BACKOFF_MAX_MS = 1_000;

    private final BusLifecycle lifecycle;
    private final ClusterRegistry clusterRegistry;
    private final BrokerEventStore eventStore;
    private final AdminPayloadCodec codec = new AdminPayloadCodec();
    private final PgDistributedLock distributedLock;

    public CommandBrokerHandler(BusLifecycle lifecycle,
                                ClusterRegistry clusterRegistry,
                                BrokerEventStore eventStore,
                                PgDistributedLock distributedLock) throws SQLException {
        this.lifecycle = lifecycle;
        this.clusterRegistry = clusterRegistry;
        this.eventStore = eventStore;
        // The shared, pool-backed lock (DistributedLockConfiguration); null = JVM-local only
        // (embedded brokers and unit tests).
        this.distributedLock = distributedLock != null ? distributedLock : new PgDistributedLock(null);

        lifecycle.subscribe(busEvent -> {
            if (!(busEvent instanceof BusEvent.BundleDiscovered discovered)) return;
            for (var handler : discovered.discovery().handlers()) {
                if (handler.getHandlerType() == HandlerType.AggregateCommandHandler) {
                    var pt = handler.getHandledPayload();
                    lifecycle.registerLocalHandler(pt, (payload, originator, deadlineMs) ->
                            handleAggregateCommand(pt, payload, originator, deadlineMs));
                    log.info("event=aggregate_command_handler_registered payloadType={}", pt);
                } else if (handler.getHandlerType() == HandlerType.CommandHandler
                        && handler.getComponentType() == ComponentType.Service) {
                    var pt = handler.getHandledPayload();
                    lifecycle.registerLocalHandler(pt, (payload, originator, deadlineMs) ->
                            handleServiceCommand(pt, payload, originator, deadlineMs));
                    log.info("event=service_command_handler_registered payloadType={}", pt);
                }
            }
        });
    }

    // ---- idempotency (G5) ----

    /**
     * The key the broker enforces for {@code cmd}: its key when the store records keys, else
     * {@code null}, so a keyed command on a store without support costs no lookup, claim or
     * lock (it runs exactly as an unkeyed one; the handler still receives the key).
     */
    private String enforcedKey(com.evento.common.modeling.messaging.message.application.CommandMessage<?> cmd,
                               String payloadType) {
        String key = cmd.getIdempotencyKey();
        if (key == null) return null;
        if (eventStore.idempotencySupported()) return key;
        if (idempotencyUnsupportedWarned.getAndIncrement() == 0) {
            log.warn("event=idempotency_key_ignored payloadType={} reason=\"the event store does not record "
                    + "idempotency keys; keyed commands run without exactly-once\"", payloadType);
        }
        return null;
    }

    /**
     * Runs a keyed command at most once. The execution looks the key up itself, in a round trip
     * it makes anyway (the aggregate story fetch, the service claim), so a keyed command costs
     * no extra lookup on its first attempt. A deadline drop is turned into the stored answer when
     * the event turns out to be stored meanwhile, so a replay is never reported as "expired, not
     * executed" once the original committed. Unkeyed commands go straight to {@code execution}.
     */
    private byte[] idempotent(String key, String payloadType, String aggregateId, EventoRequest request,
                              CommandExecution execution) throws Exception {
        if (key == null) return execution.run();
        try {
            return execution.run();
        } catch (RequestExpiredException e) {
            var late = answerRecorded(key, payloadType, aggregateId, request, REPLAY_AFTER_EXPIRY);
            if (late != null) return late;
            throw e;
        }
    }

    @FunctionalInterface
    private interface CommandExecution {
        byte[] run() throws Exception;
    }

    /**
     * The answer for a key that is already settled, read in one lookup: the stored event when it
     * belongs to this command (same payload type and aggregate id), an empty result when the
     * key's service command completed without an event, and
     * {@code IdempotencyKeyMismatchException} when the key is recorded — stored, completed or
     * in flight — for a different command. {@code null} when nothing settles it yet (nothing
     * recorded, or a claim of this same command still in flight).
     */
    private byte[] answerRecorded(String key, String payloadType, String aggregateId, EventoRequest request,
                                  String stage) {
        var record = eventStore.findIdempotencyRecord(key).orElse(null);
        return answerRecorded(record, key, payloadType, aggregateId, request, stage);
    }

    private byte[] answerRecorded(BrokerEventStore.IdempotencyRecord record, String key, String payloadType,
                                  String aggregateId, EventoRequest request, String stage) {
        if (record == null) return null;
        if (!record.belongsTo(payloadType, aggregateId)) {
            return mismatch(record, key, payloadType, aggregateId, request, stage);
        }
        if (record.event() != null) {
            return replay(key, payloadType, request, stage, record.event().eventSequenceNumber(),
                    record.event().eventMessage());
        }
        if (record.claim() == BrokerEventStore.ClaimState.COMPLETED) {
            return replay(key, payloadType, request, stage, -1,
                    new com.evento.common.modeling.messaging.message.application.ServiceEventMessage(null));
        }
        return null;
    }

    /**
     * {@code body} (a stored event, or an empty service result) as this request's response. Logs
     * {@code event=idempotent_replay} and counts {@code evento.server.commands.idempotent.replayed}.
     */
    private byte[] replay(String key, String payloadType, EventoRequest request, String stage, long seq,
                          Object body) {
        idempotentReplays.incrementAndGet();
        idempotentReplaysByStage.computeIfAbsent(stage, k -> new AtomicLong()).incrementAndGet();
        log.info("event=idempotent_replay payloadType={} idempotencyKey={} correlationId={} stage={} seq={}",
                payloadType, key, request.getCorrelationId(), stage, seq);
        var reply = new EventoResponse();
        reply.setCorrelationId(request.getCorrelationId());
        reply.setBody((java.io.Serializable) body);
        return codec.encodeResponse(reply);
    }

    /**
     * The key is recorded for another command: refuse, never replay. A positional derived key
     * shifted onto another command, a key forwarded to a nested command, a creation whose
     * aggregate id changes per attempt, or a caller that minted one key for two intents.
     */
    private byte[] mismatch(BrokerEventStore.IdempotencyRecord record, String key, String payloadType,
                            String aggregateId, EventoRequest request, String stage) {
        idempotencyMismatches.incrementAndGet();
        log.warn("event=idempotency_key_mismatch payloadType={} aggregateId={} idempotencyKey={} correlationId={} "
                        + "stage={} recordedPayloadType={} recordedAggregateId={} recorded={}",
                payloadType, aggregateId, key, request.getCorrelationId(), stage, record.commandType(),
                record.aggregateId(), record.event() != null ? "event" : record.claim());
        return exceptionResponse(request.getCorrelationId(), new IdempotencyKeyMismatchException(
                "idempotency key reused by a different command: '" + key + "' is recorded for "
                        + record.commandType() + " on aggregate " + record.aggregateId() + ", not for "
                        + payloadType + " on aggregate " + aggregateId + "; nothing was executed"));
    }

    /**
     * Another execution of the key may be in flight and this request stops waiting for it:
     * indeterminate, never "not executed" (it may still commit).
     */
    private byte[] inProgress(String key, String payloadType, EventoRequest request, String reason) {
        idempotencyInProgress.incrementAndGet();
        log.info("event=idempotency_key_in_progress payloadType={} idempotencyKey={} correlationId={} reason=\"{}\"",
                payloadType, key, request.getCorrelationId(), reason);
        return exceptionResponse(request.getCorrelationId(), new IdempotencyKeyInProgressException(
                "idempotency key '" + key + "' (" + payloadType + "): " + reason
                        + "; the outcome is not known yet, retry with the same key"));
    }

    /** Commands answered with a stored event (or an empty stored result) instead of being executed (G5). */
    public long idempotentReplayedCount() {
        return idempotentReplays.get();
    }

    /** Replays at one stage ({@link #REPLAY_BEFORE_LOCK}, {@link #REPLAY_APPEND_CONFLICT}, ...). */
    public long idempotentReplayedCount(String stage) {
        var c = idempotentReplaysByStage.get(stage);
        return c == null ? 0L : c.get();
    }

    /** Commands refused because their key is recorded for a different command. */
    public long idempotencyMismatchCount() {
        return idempotencyMismatches.get();
    }

    /** Commands answered "in progress" because another execution of their key did not end in time. */
    public long idempotencyInProgressCount() {
        return idempotencyInProgress.get();
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        FunctionCounter.builder("evento.server.commands.idempotent.replayed", this,
                        CommandBrokerHandler::idempotentReplayedCount)
                .description("Commands answered with the event stored under their idempotency key, not executed again")
                .register(registry);
        FunctionCounter.builder("evento.server.commands.idempotent.mismatched", this,
                        CommandBrokerHandler::idempotencyMismatchCount)
                .description("Commands refused because their idempotency key is recorded for a different command")
                .register(registry);
        FunctionCounter.builder("evento.server.commands.idempotent.in_progress", this,
                        CommandBrokerHandler::idempotencyInProgressCount)
                .description("Keyed commands answered 'in progress' while another execution of the key was in flight")
                .register(registry);
    }

    // ---- aggregate command ----

    private byte[] handleAggregateCommand(String commandPayloadType, byte[] payload,
                                          NodeAddress originator, long deadlineMs) throws Exception {
        var eventoRequest = codec.decodeRequest(payload);
        var cmd = (DomainCommandMessage) eventoRequest.getBody();
        String key = enforcedKey(cmd, commandPayloadType);
        return idempotent(key, commandPayloadType, cmd.getAggregateId(), eventoRequest,
                () -> executeAggregateCommand(commandPayloadType, eventoRequest, cmd, key, originator, deadlineMs));
    }

    /**
     * The aggregate path. With a key, concurrent duplicates are serialised by the aggregate lock
     * (a domain command locks on its aggregate id unless it names another lock) and the key is
     * checked under it, in the same statement that fetches the story (no round trip of its own):
     * the second duplicate must not run the handler against a story that already holds the first
     * one's event (a creation would be refused as "already exists" instead of replayed). There is
     * no lookup before the lock any more: it cost a round trip on every first attempt to save the
     * lock wait of a replay, the rare case. Without a lock, or with a lost one, the unique index
     * is the fence: the losing append replays. Aggregate handlers are pure, so a handler run whose
     * append then loses changes nothing.
     *
     * <p>A keyed command whose deadline passes while it waits for the aggregate lock may be
     * waiting behind an execution of its own key, which can still commit: it is answered
     * {@code IdempotencyKeyInProgressException} (indeterminate), not the determinate "expired".
     */
    private byte[] executeAggregateCommand(String commandPayloadType, EventoRequest eventoRequest,
                                           DomainCommandMessage cmd, String key,
                                           NodeAddress originator, long deadlineMs) throws Exception {
        String lockKey = cmd.getLockId() != null ? RESOURCE_LOCK_PREFIX + cmd.getLockId() : null;
        boolean lockAcquired = false;
        try {
            if (lockKey != null) {
                try {
                    acquireWithin(lockKey, deadlineMs, commandPayloadType, eventoRequest.getCorrelationId());
                } catch (RequestExpiredException expired) {
                    if (key == null) throw expired;
                    var late = answerRecorded(key, commandPayloadType, cmd.getAggregateId(), eventoRequest,
                            REPLAY_AFTER_EXPIRY);
                    if (late != null) return late;
                    return inProgress(key, commandPayloadType, eventoRequest,
                            "the deadline passed waiting for the aggregate lock, which an execution of the same "
                                    + "key may hold");
                }
                lockAcquired = true;
            }

            // The key is checked with the story, in the same round trip, under the lock.
            var keyed = eventStore.fetchAggregateStory(
                    cmd.getAggregateId(),
                    cmd.isInvalidateAggregateCaches(),
                    cmd.isInvalidateAggregateSnapshot(),
                    key);
            if (key != null) {
                var recorded = answerRecorded(keyed.record(), key, commandPayloadType, cmd.getAggregateId(),
                        eventoRequest, REPLAY_WITH_STORY);
                if (recorded != null) return recorded;
            }
            var story = keyed.story();

            var decorated = new DecoratedDomainCommandMessage();
            decorated.setCommandMessage(cmd);
            decorated.setSerializedAggregateState(story.state());
            decorated.setEventStream(story.events());

            var forwardRequest = new EventoRequest();
            forwardRequest.setCorrelationId(eventoRequest.getCorrelationId());
            forwardRequest.setSourceBundleId(eventoRequest.getSourceBundleId());
            forwardRequest.setSourceInstanceId(eventoRequest.getSourceInstanceId());
            forwardRequest.setSourceBundleVersion(eventoRequest.getSourceBundleVersion());
            forwardRequest.setBody(decorated);
            forwardRequest.setTimeout(eventoRequest.getTimeout());
            forwardRequest.setUnit(eventoRequest.getUnit());
            forwardRequest.setTimestamp(eventoRequest.getTimestamp());

            long timeoutMs = forwardBudget(eventoRequest.getUnit().toMillis(eventoRequest.getTimeout()),
                    deadlineMs, commandPayloadType, eventoRequest.getCorrelationId(), true);
            var response = forwardWithin(commandPayloadType,
                    codec.encodeRequest(forwardRequest), timeoutMs, originator, deadlineMs,
                    eventoRequest.getCorrelationId(), true);

            if (response.isError()) {
                var recorded = key == null ? null
                        : answerRecorded(key, commandPayloadType, cmd.getAggregateId(), eventoRequest, REPLAY_AFTER_HANDLER_ERROR);
                if (recorded != null) return recorded;
                return errorResponse(eventoRequest.getCorrelationId(),
                        response.error() != null ? response.error().message() : "command failed");
            }

            var eventoResponse = codec.decodeResponse(response.payload());
            if (eventoResponse.getBody() instanceof ExceptionWrapper) {
                // Without the aggregate lock a duplicate can see the original's event in its
                // story and be refused by the domain ("already created"): that refusal is the
                // original's success, not a failure.
                var recorded = key == null ? null
                        : answerRecorded(key, commandPayloadType, cmd.getAggregateId(), eventoRequest, REPLAY_AFTER_HANDLER_ERROR);
                if (recorded != null) return recorded;
                return codec.encodeResponse(eventoResponse);
            }

            var cmdResponse = (DomainCommandResponseMessage) eventoResponse.getBody();
            var eventMessage = cmdResponse.getDomainEventMessage();

            // The bundle computed the event from the story fetched under the lock. If the
            // session owning the lock died meanwhile (failover, socket timeout), another server
            // may already be appending from the same story: refuse to commit a fork. The window
            // between this check and the INSERT stays open; closing it needs a DB-level fence
            // (aggregate_seq, see CLUSTER-READINESS G6).
            if (lockAcquired) distributedLock.verifyHeld(lockKey);
            // The bundle may have answered after the caller gave up (a slow handler, a
            // retried forward): its timeout already told the caller "failed", so storing
            // the event now would apply a command the caller may retry.
            checkDeadline(deadlineMs, BusLifecycle.EXPIRED_STAGE_BEFORE_APPEND, commandPayloadType,
                    eventoRequest.getCorrelationId());
            long seqNum;
            try {
                seqNum = eventStore.publishEvent(eventMessage, cmd.getAggregateId(), key, commandPayloadType);
            } catch (BrokerEventStore.IdempotencyKeyConflictException conflict) {
                // A duplicate on another server (or without the lock) appended first. This
                // handler run was pure; answer with what was stored (or refuse, if the key
                // belongs to another command), write no snapshot.
                var recorded = answerRecorded(key, commandPayloadType, cmd.getAggregateId(), eventoRequest,
                        REPLAY_APPEND_CONFLICT);
                if (recorded != null) return recorded;
                throw conflict;
            }
            log.debug("event=domain_event_stored payloadType={} aggregateId={} seq={}",
                    commandPayloadType, cmd.getAggregateId(), seqNum);

            if (cmdResponse.getSerializedAggregateState() != null) {
                eventStore.saveSnapshot(cmd.getAggregateId(), seqNum,
                        cmdResponse.getSerializedAggregateState());
            }

            if (cmdResponse.isAggregateDeleted()) {
                eventStore.deleteAggregate(cmd.getAggregateId());
                log.debug("event=aggregate_deleted aggregateId={}", cmd.getAggregateId());
            }

            var reply = new EventoResponse();
            reply.setCorrelationId(eventoRequest.getCorrelationId());
            reply.setBody(eventMessage);
            return codec.encodeResponse(reply);

        } finally {
            if (lockAcquired) {
                releaseQuietly(lockKey);
            }
        }
    }

    // ---- service command ----

    private byte[] handleServiceCommand(String commandPayloadType, byte[] payload,
                                        NodeAddress originator, long deadlineMs) throws Exception {
        var eventoRequest = codec.decodeRequest(payload);
        var cmd = (ServiceCommandMessage) eventoRequest.getBody();
        String key = enforcedKey(cmd, commandPayloadType);
        if (key == null) {
            return executeServiceCommand(commandPayloadType, payload, eventoRequest, cmd, null, null,
                    originator, deadlineMs);
        }
        return idempotent(key, commandPayloadType, cmd.getAggregateId(), eventoRequest,
                () -> executeKeyedServiceCommand(commandPayloadType, payload, eventoRequest, cmd, key,
                        originator, deadlineMs));
    }

    /**
     * A keyed service command. Its handler has side effects, so the unique index alone (which
     * only stops the second append) is not enough: the execution first <em>claims</em> the key
     * (a row in {@code es__idempotency_claims}, inserted or taken over from a dead owner whose
     * lease ran out). The claim statement also reads what is recorded for the key, so one round
     * trip claims or answers ({@link BrokerEventStore#claimOrFindIdempotencyKey}); no lookup runs
     * before it and none under it (an owner marks its claim completed in its append statement, so
     * a claim can never be won for a key whose event is stored). A duplicate that finds the claim
     * held polls it until it is settled (stored event, empty completion, or released after a
     * failure, in which case it claims and runs) or until it stops waiting, and then answers
     * {@code IdempotencyKeyInProgressException}.
     *
     * <p>The claim's first lease covers the wait for the command's lock and the forward (what is
     * left of the caller's deadline + the command's timeout + {@value #CLAIM_LEASE_GRACE_MS} ms),
     * so it is renewed only when a lock wait without a deadline outlasted it. Before, every keyed
     * service command paid a lookup, the claim, a re-check, a renewal and a release: five round
     * trips to the event-store primary where an unkeyed one pays none; now one (the claim).
     *
     * <p>The claim is a row, not a session lock: neither the owner nor the waiters hold a
     * connection for it. An earlier design took a cluster-wide {@code IDEMPOTENCY:<key>} advisory
     * lock, pinning a lock-pool connection for the whole handler run on top of the command's own
     * {@code RESOURCE:} lock; with the pool full of such holders every one of them waited for a
     * second connection that none could release (hold-and-wait), and every consumer-sent service
     * command is keyed. Now every command holds at most one lock connection.
     *
     * <p>What the broker alone cannot close: a handler that ran while its event was not stored
     * (the append failed, the server died between the answer and the append). The claim is
     * released on failure, and a dead owner's claim expires with its lease; a retry then forwards
     * the command again. The service bundle closes it with a durable outcome store
     * ({@code EventoBundle.Builder#setServiceCommandOutcomeStore}): the retry is answered from
     * the recorded outcome without running the handler, and the broker stores that answer.
     */
    private byte[] executeKeyedServiceCommand(String commandPayloadType, byte[] payload, EventoRequest eventoRequest,
                                              ServiceCommandMessage cmd, String key,
                                              NodeAddress originator, long deadlineMs) throws Exception {
        String aggregateId = cmd.getAggregateId();
        String owner = claimOwnerPrefix + claimOwnerSeq.incrementAndGet();
        long commandTimeoutMs = eventoRequest.getUnit().toMillis(eventoRequest.getTimeout());
        long waitBoundMs = deadlineMs != Long.MAX_VALUE ? deadlineMs
                : BusLifecycle.monotonicMillis() + (commandTimeoutMs > 0 ? commandTimeoutMs : DEFAULT_KEY_WAIT_MS);
        long pollMs = CLAIM_POLL_INITIAL_MS;
        boolean waited = false;
        Claim claim;
        while (true) {
            long now = BusLifecycle.monotonicMillis();
            long untilDeadlineMs = Math.max(0L, waitBoundMs - now);
            long forwardMs = commandTimeoutMs > 0 ? commandTimeoutMs : untilDeadlineMs;
            long leaseMs = untilDeadlineMs + forwardMs + CLAIM_LEASE_GRACE_MS;
            var attempt = eventStore.claimOrFindIdempotencyKey(key, commandPayloadType, aggregateId, owner, leaseMs);
            if (attempt.claimed()) {
                // Measured from before the statement was sent: the database starts the lease
                // later, so this local end is a conservative one.
                claim = new Claim(owner, now + leaseMs);
                break;
            }
            var settled = answerRecorded(attempt.record(), key, commandPayloadType, aggregateId, eventoRequest,
                    waited ? REPLAY_AFTER_WAIT : REPLAY_BEFORE_LOCK);
            if (settled != null) return settled;
            // Held by another execution (or claimed concurrently and not visible yet): wait for it.
            if (BusLifecycle.monotonicMillis() >= waitBoundMs) {
                return inProgress(key, commandPayloadType, eventoRequest,
                        "another execution of this key was still in flight when this request stopped waiting");
            }
            waited = true;
            Thread.sleep(Math.min(pollMs, Math.max(1L, waitBoundMs - BusLifecycle.monotonicMillis())));
            pollMs = Math.min(pollMs * 2, CLAIM_POLL_MAX_MS);
        }
        try {
            return executeServiceCommand(commandPayloadType, payload, eventoRequest, cmd, key, claim,
                    originator, deadlineMs);
        } finally {
            if (!claim.settled) {
                // After a failure (an error answer, a lost forward, a failed append): a retry may
                // run the command again. On success the claim was completed by the append itself.
                try {
                    eventStore.releaseIdempotencyClaim(key, owner);
                } catch (RuntimeException e) {
                    log.warn("event=idempotency_claim_release_failed idempotencyKey={} error=\"{}\" "
                            + "reason=\"the claim expires with its lease\"", key, e.getMessage());
                }
            }
        }
    }

    /** A claim this broker holds on a key, and whether its outcome is settled (nothing to release). */
    private static final class Claim {
        final String owner;
        /** Conservative local end of the lease, on {@link BusLifecycle#monotonicMillis()}. */
        long leaseEndsAtMs;
        boolean settled;

        Claim(String owner, long leaseEndsAtMs) {
            this.owner = owner;
            this.leaseEndsAtMs = leaseEndsAtMs;
        }
    }

    /**
     * The service path. With a key ({@code claim} non-null), the key is already claimed by
     * {@link #executeKeyedServiceCommand}: the lease is renewed before the forward only when it
     * might not cover it, and the outcome is recorded after it (the event with the key, which
     * completes the claim in the same statement, or an empty completion).
     */
    private byte[] executeServiceCommand(String commandPayloadType, byte[] payload, EventoRequest eventoRequest,
                                         ServiceCommandMessage cmd, String key, Claim claim,
                                         NodeAddress originator, long deadlineMs) throws Exception {
        String lockKey = cmd.getLockId() != null ? RESOURCE_LOCK_PREFIX + cmd.getLockId() : null;
        boolean lockAcquired = false;
        try {
            if (lockKey != null) {
                acquireWithin(lockKey, deadlineMs, commandPayloadType, eventoRequest.getCorrelationId());
                lockAcquired = true;
            }

            // Forward the original payload unchanged — the bundle decodes EventoRequest{body: ServiceCommandMessage}.
            // Once forwarded, the broker waits the command's own timeout rather than what is
            // left of the caller's: a service handler that answers late has still run, and its
            // event must be stored (see the class javadoc).
            long timeoutMs = forwardBudget(eventoRequest.getUnit().toMillis(eventoRequest.getTimeout()),
                    deadlineMs, commandPayloadType, eventoRequest.getCorrelationId(), false);
            if (claim != null && BusLifecycle.monotonicMillis() + timeoutMs + CLAIM_LEASE_GRACE_MS > claim.leaseEndsAtMs) {
                // Only after a lock wait the first lease did not cover (a command without deadline).
                long renewMs = timeoutMs + CLAIM_LEASE_GRACE_MS;
                long renewedAt = BusLifecycle.monotonicMillis();
                if (!eventStore.renewIdempotencyClaim(key, claim.owner, renewMs)) {
                    // Our lease ran out (a long lock wait) and a retry took the key over: it runs it.
                    claim.settled = true; // not ours any more: nothing to release
                    return inProgress(key, commandPayloadType, eventoRequest,
                            "the claim on the key expired before the forward and another execution took it over");
                }
                claim.leaseEndsAtMs = renewedAt + renewMs;
            }
            var response = forwardWithin(commandPayloadType, payload, timeoutMs, originator, deadlineMs,
                    eventoRequest.getCorrelationId(), false);

            if (response.isError()) {
                return errorResponse(eventoRequest.getCorrelationId(),
                        response.error() != null ? response.error().message() : "command failed");
            }

            var eventoResponse = codec.decodeResponse(response.payload());
            if (eventoResponse.getBody() instanceof ExceptionWrapper) {
                return codec.encodeResponse(eventoResponse);
            }

            var serviceEvent = (com.evento.common.modeling.messaging.message.application.ServiceEventMessage)
                    eventoResponse.getBody();

            // Only store if the event payload class is known (guard against corrupted records)
            if (serviceEvent.getSerializedPayload() != null
                    && serviceEvent.getSerializedPayload().getObjectClass() != null) {
                if (lockAcquired) distributedLock.verifyHeld(lockKey);
                // No deadline check here, unlike the aggregate path: the service handler has
                // already run, with whatever side effects it has (a payment, a mail). Dropping
                // its event would hide an effect that happened from every saga, projector and
                // observer, and tell a retrying caller "not executed". The event is the record
                // of the effect, so it is always stored; a caller that gave up has seen an
                // indeterminate timeout, which is the truth.
                if (deadlineMs != Long.MAX_VALUE && BusLifecycle.monotonicMillis() >= deadlineMs) {
                    log.info("event=service_event_stored_after_deadline payloadType={} correlationId={} overdueMs={}",
                            commandPayloadType, eventoRequest.getCorrelationId(),
                            BusLifecycle.monotonicMillis() - deadlineMs);
                }
                try {
                    if (claim != null) {
                        eventStore.publishEvent(serviceEvent, cmd.getAggregateId(), key, commandPayloadType, claim.owner);
                        claim.settled = true;
                    } else {
                        eventStore.publishEvent(serviceEvent, cmd.getAggregateId(), key, commandPayloadType);
                    }
                } catch (BrokerEventStore.IdempotencyKeyConflictException conflict) {
                    // Only possible when the claim expired and was taken over while this handler
                    // ran (its lease covers the forward, so: a server paused or partitioned past
                    // it): a duplicate ran the handler too, unless the bundle answered both from
                    // its outcome store. The first event stands.
                    log.warn("event=idempotent_duplicate_execution payloadType={} idempotencyKey={} correlationId={} "
                                    + "reason=\"the handler ran for a key that another execution stored first\"",
                            commandPayloadType, key, eventoRequest.getCorrelationId());
                    var recorded = answerRecorded(key, commandPayloadType, cmd.getAggregateId(), eventoRequest,
                            REPLAY_APPEND_CONFLICT);
                    if (recorded != null) return recorded;
                    throw conflict;
                }
                log.debug("event=service_event_stored payloadType={}", commandPayloadType);
            } else if (claim != null) {
                // A handler that returns nothing (void or null) stores no event, so without this
                // its key would be recorded nowhere and every retry would run it again.
                if (!eventStore.completeIdempotencyClaim(key, claim.owner)) {
                    log.warn("event=idempotency_claim_lost payloadType={} idempotencyKey={} correlationId={} "
                                    + "reason=\"the claim expired and was taken over while the handler ran\"",
                            commandPayloadType, key, eventoRequest.getCorrelationId());
                }
                claim.settled = true;
            }

            return codec.encodeResponse(eventoResponse);

        } finally {
            if (lockAcquired) {
                releaseQuietly(lockKey);
            }
        }
    }

    /**
     * Acquires the command's lock, waiting no longer than the caller does (deadline on
     * {@link BusLifecycle#monotonicMillis()}). A wait cut short
     * by the caller's deadline is a drop ({@code lock_wait}); one cut short by the lock's own
     * bound, while the caller still waits, stays a {@code LockAcquisitionTimeoutException}.
     */
    private void acquireWithin(String lockKey, long deadlineMs, String payloadType, String correlationId) {
        if (deadlineMs == Long.MAX_VALUE) {
            distributedLock.acquire(lockKey);
            return;
        }
        long remainingMs = deadlineMs - BusLifecycle.monotonicMillis();
        if (remainingMs <= 0) {
            throw lifecycle.expiredDrop(BusLifecycle.EXPIRED_STAGE_LOCK_WAIT, payloadType, correlationId, deadlineMs);
        }
        try {
            distributedLock.acquire(lockKey, Duration.ofMillis(remainingMs));
        } catch (LockAcquisitionTimeoutException e) {
            if (BusLifecycle.monotonicMillis() >= deadlineMs) {
                throw lifecycle.expiredDrop(BusLifecycle.EXPIRED_STAGE_LOCK_WAIT, payloadType, correlationId, deadlineMs);
            }
            throw e;
        }
    }

    /**
     * How long the forward to the handler bundle may take: for an aggregate command
     * ({@code capAtDeadline}), what is left of the caller's deadline (the lock wait and the
     * story fetch already used part of it), capped by the command's own timeout; for a
     * service command, the command's own timeout. Throws the {@code before_forward} drop
     * when the deadline has already passed.
     */
    private long forwardBudget(long commandTimeoutMs, long deadlineMs, String payloadType, String correlationId,
                               boolean capAtDeadline) {
        checkDeadline(deadlineMs, BusLifecycle.EXPIRED_STAGE_BEFORE_FORWARD, payloadType, correlationId);
        if (deadlineMs == Long.MAX_VALUE) return commandTimeoutMs;
        if (!capAtDeadline && commandTimeoutMs > 0) return commandTimeoutMs;
        long remainingMs = deadlineMs - BusLifecycle.monotonicMillis();
        return commandTimeoutMs > 0 ? Math.min(commandTimeoutMs, remainingMs) : remainingMs;
    }

    /**
     * {@link #forwardOrRetry} within the caller's deadline. For an aggregate command
     * ({@code pureHandler}), a forward that fails once the deadline has passed (the handler
     * bundle had not answered in time; a late answer is discarded) is a {@code before_append}
     * drop: the handler only computed an event from the story, and the broker appends nothing,
     * so "not executed" is true. A service handler may have run its side effects even though
     * its answer never arrived, so its failure stays the indeterminate error it is.
     */
    private Response forwardWithin(String payloadType, byte[] payload, long timeoutMs, NodeAddress originator,
                                   long deadlineMs, String correlationId, boolean pureHandler) throws Exception {
        try {
            return forwardOrRetry(payloadType, payload, timeoutMs, originator);
        } catch (Exception e) {
            if (pureHandler && deadlineMs != Long.MAX_VALUE && BusLifecycle.monotonicMillis() >= deadlineMs) {
                throw lifecycle.expiredDrop(BusLifecycle.EXPIRED_STAGE_BEFORE_APPEND, payloadType, correlationId,
                        deadlineMs);
            }
            throw e;
        }
    }

    private void checkDeadline(long deadlineMs, String stage, String payloadType, String correlationId) {
        if (deadlineMs != Long.MAX_VALUE && BusLifecycle.monotonicMillis() >= deadlineMs) {
            throw lifecycle.expiredDrop(stage, payloadType, correlationId, deadlineMs);
        }
    }

    /**
     * Releases a command lock from a {@code finally} block without replacing the command's
     * outcome: by then the event is either stored (the reply must reach the caller, or it will
     * retry and double-apply) or the command already failed with its own exception. A lock lost
     * before the append was refused by {@code verifyHeld}; one lost after it is logged
     * ({@code event=lock_lost}) by the lock itself.
     */
    private void releaseQuietly(String lockKey) {
        try {
            distributedLock.release(lockKey);
        } catch (RuntimeException e) {
            log.warn("event=command_lock_release_failed key={} error=\"{}\"", lockKey, e.getMessage());
        }
    }

    /**
     * Forwards {@code payload} to any available handler node for {@code payloadType},
     * retrying with exponential back-off when the chosen node is temporarily
     * un-reachable (DEGRADED / CONNECTING). Re-picks the destination on each
     * attempt so that a recovered or different node can be chosen. Stops when
     * the original command timeout has elapsed.
     *
     * <p>{@code originator} is the bundle that sent the command to the broker;
     * the pick is made on its behalf so a {@code LOCALITY_PREFERRED} broker keeps
     * the command in the caller's zone even though the forward itself is
     * server-initiated.
     */
    private Response forwardOrRetry(String payloadType, byte[] payload, long timeoutMs,
                                    NodeAddress originator) throws Exception {
        long deadlineMs = BusLifecycle.monotonicMillis() + timeoutMs;
        long backoffMs = SEND_RETRY_BACKOFF_INITIAL_MS;
        int attempt = 0;
        while (true) {
            long remainingMs = deadlineMs - BusLifecycle.monotonicMillis();
            if (remainingMs <= 0) {
                throw new TimeoutException("forward timed out after " + attempt + " attempt(s) for " + payloadType);
            }
            var destination = clusterRegistry.pickFor(payloadType, originator)
                    .orElseThrow(() -> new IllegalStateException("no available handler for " + payloadType));
            var future = lifecycle.forward(destination, payloadType, payload, Duration.ofMillis(remainingMs));
            try {
                return future.get(remainingMs, TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof SendFailedException sfe && sfe.connectionState().isActive()) {
                    long sleep = Math.min(backoffMs, deadlineMs - BusLifecycle.monotonicMillis());
                    if (sleep > 0) {
                        log.warn("event=forward_retry attempt={} payloadType={} state={} backoffMs={}",
                                ++attempt, payloadType, sfe.connectionState(), sleep);
                        Thread.sleep(sleep);
                        backoffMs = Math.min(backoffMs * 2, SEND_RETRY_BACKOFF_MAX_MS);
                        continue;
                    }
                }
                throw e;
            }
        }
    }

    private byte[] exceptionResponse(String correlationId, RuntimeException exception) {
        var resp = new EventoResponse();
        resp.setCorrelationId(correlationId);
        resp.setBody(new ExceptionWrapper(exception));
        return codec.encodeResponse(resp);
    }

    private byte[] errorResponse(String correlationId, String message) {
        var resp = new EventoResponse();
        resp.setCorrelationId(correlationId);
        resp.setBody(new ExceptionWrapper(new RuntimeException(message)));
        return codec.encodeResponse(resp);
    }
}