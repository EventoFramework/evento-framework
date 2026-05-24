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
import com.evento.common.utils.PgDistributedLock;
import com.evento.server.bus.NodeAddress;
import com.evento.server.bus.v2.event.BusEvent;
import com.evento.transport.SendFailedException;
import com.evento.transport.message.Response;
import com.evento.server.bus.v2.lifecycle.BusLifecycle;
import com.evento.server.bus.v2.registry.ClusterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
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
                                DataSource dataSource) throws SQLException {
        this.lifecycle = lifecycle;
        this.clusterRegistry = clusterRegistry;
        this.eventStore = eventStore;
        this.distributedLock = new PgDistributedLock(dataSource);

        lifecycle.subscribe(busEvent -> {
            if (!(busEvent instanceof BusEvent.BundleDiscovered discovered)) return;
            for (var handler : discovered.discovery().handlers()) {
                if (handler.getHandlerType() == HandlerType.AggregateCommandHandler) {
                    var pt = handler.getHandledPayload();
                    lifecycle.registerLocalHandler(pt, payload -> handleAggregateCommand(pt, payload));
                    log.info("event=aggregate_command_handler_registered payloadType={}", pt);
                } else if (handler.getHandlerType() == HandlerType.CommandHandler
                        && handler.getComponentType() == ComponentType.Service) {
                    var pt = handler.getHandledPayload();
                    lifecycle.registerLocalHandler(pt, payload -> handleServiceCommand(pt, payload));
                    log.info("event=service_command_handler_registered payloadType={}", pt);
                }
            }
        });
    }

    // ---- aggregate command ----

    private byte[] handleAggregateCommand(String commandPayloadType, byte[] payload) throws Exception {
        var eventoRequest = codec.decodeRequest(payload);
        var cmd = (DomainCommandMessage) eventoRequest.getBody();

        String lockKey = cmd.getLockId() != null ? RESOURCE_LOCK_PREFIX + cmd.getLockId() : null;
        boolean lockAcquired = false;
        try {
            if (lockKey != null) {
                distributedLock.acquire(lockKey);
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

            long timeoutMs = eventoRequest.getUnit().toMillis(eventoRequest.getTimeout());
            var response = forwardOrRetry(commandPayloadType,
                    codec.encodeRequest(forwardRequest), timeoutMs);

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
                distributedLock.release(lockKey);
            }
        }
    }

    // ---- service command ----

    private byte[] handleServiceCommand(String commandPayloadType, byte[] payload) throws Exception {
        var eventoRequest = codec.decodeRequest(payload);
        var cmd = (ServiceCommandMessage) eventoRequest.getBody();

        String lockKey = cmd.getLockId() != null ? RESOURCE_LOCK_PREFIX + cmd.getLockId() : null;
        boolean lockAcquired = false;
        try {
            if (lockKey != null) {
                distributedLock.acquire(lockKey);
                lockAcquired = true;
            }

            // Forward the original payload unchanged — the bundle decodes EventoRequest{body: ServiceCommandMessage}.
            long timeoutMs = eventoRequest.getUnit().toMillis(eventoRequest.getTimeout());
            var response = forwardOrRetry(commandPayloadType, payload, timeoutMs);

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
                eventStore.publishEvent(serviceEvent, cmd.getAggregateId());
                log.debug("event=service_event_stored payloadType={}", commandPayloadType);
            }

            return codec.encodeResponse(eventoResponse);

        } finally {
            if (lockAcquired) {
                distributedLock.release(lockKey);
            }
        }
    }

    /**
     * Forwards {@code payload} to any available handler node for {@code payloadType},
     * retrying with exponential back-off when the chosen node is temporarily
     * un-reachable (DEGRADED / CONNECTING). Re-picks the destination on each
     * attempt so that a recovered or different node can be chosen. Stops when
     * the original command timeout has elapsed.
     */
    private Response forwardOrRetry(String payloadType, byte[] payload, long timeoutMs) throws Exception {
        long deadlineMs = System.currentTimeMillis() + timeoutMs;
        long backoffMs = SEND_RETRY_BACKOFF_INITIAL_MS;
        int attempt = 0;
        while (true) {
            long remainingMs = deadlineMs - System.currentTimeMillis();
            if (remainingMs <= 0) {
                throw new TimeoutException("forward timed out after " + attempt + " attempt(s) for " + payloadType);
            }
            var destination = clusterRegistry.pick(payloadType)
                    .orElseThrow(() -> new IllegalStateException("no available handler for " + payloadType));
            var future = lifecycle.forward(destination, payloadType, payload, Duration.ofMillis(remainingMs));
            try {
                return future.get(remainingMs, TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof SendFailedException sfe && sfe.connectionState().isActive()) {
                    long sleep = Math.min(backoffMs, deadlineMs - System.currentTimeMillis());
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