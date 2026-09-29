package com.evento.server.es;

import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
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
import com.evento.transport.SendFailedException;
import com.evento.transport.message.Response;
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
 * caller; that window needs an idempotency key (CLUSTER-READINESS G5).
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
 *   <li>Optionally acquire the distributed lock on {@code "RESOURCE:" + lockId}
 *       (only if the command carries a non-null {@code lockId}).</li>
 *   <li>Forward the {@code ServiceCommandMessage} unchanged to the service bundle.</li>
 *   <li>If the response is an {@code EventMessage} with a non-null
 *       {@code objectClass}, persist it in {@link EventStore}.</li>
 *   <li>Return the {@code ServiceEventMessage} to the original caller.</li>
 * </ol>
 */
@Component
public class CommandBrokerHandler {

    private static final Logger log = LoggerFactory.getLogger(CommandBrokerHandler.class);
    private static final String RESOURCE_LOCK_PREFIX = "RESOURCE:";
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

    // ---- aggregate command ----

    private byte[] handleAggregateCommand(String commandPayloadType, byte[] payload,
                                          NodeAddress originator, long deadlineMs) throws Exception {
        var eventoRequest = codec.decodeRequest(payload);
        var cmd = (DomainCommandMessage) eventoRequest.getBody();

        String lockKey = cmd.getLockId() != null ? RESOURCE_LOCK_PREFIX + cmd.getLockId() : null;
        boolean lockAcquired = false;
        try {
            if (lockKey != null) {
                acquireWithin(lockKey, deadlineMs, commandPayloadType, eventoRequest.getCorrelationId());
                lockAcquired = true;
            }

            var story = eventStore.fetchAggregateStory(
                    cmd.getAggregateId(),
                    cmd.isInvalidateAggregateCaches(),
                    cmd.isInvalidateAggregateSnapshot());

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
                return errorResponse(eventoRequest.getCorrelationId(),
                        response.error() != null ? response.error().message() : "command failed");
            }

            var eventoResponse = codec.decodeResponse(response.payload());
            if (eventoResponse.getBody() instanceof ExceptionWrapper) {
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
            long seqNum = eventStore.publishEvent(eventMessage, cmd.getAggregateId());
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
                eventStore.publishEvent(serviceEvent, cmd.getAggregateId());
                log.debug("event=service_event_stored payloadType={}", commandPayloadType);
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

    private byte[] errorResponse(String correlationId, String message) {
        var resp = new EventoResponse();
        resp.setCorrelationId(correlationId);
        resp.setBody(new ExceptionWrapper(new RuntimeException(message)));
        return codec.encodeResponse(resp);
    }
}