package com.evento.server.es;

import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.messaging.consumer.EventFetchRequest;
import com.evento.common.messaging.consumer.EventFetchResponse;
import com.evento.common.messaging.consumer.EventLastSequenceNumberRequest;
import com.evento.common.messaging.consumer.EventLastSequenceNumberResponse;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.server.bus.lifecycle.BusLifecycle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Registers server-local handlers for event-store query requests so the broker
 * answers {@link EventFetchRequest} and {@link EventLastSequenceNumberRequest}
 * directly from the {@link EventStore} rather than forwarding them to a bundle.
 */
@Component
public class EventStoreRequestHandlerBridge {

    private static final Logger logger = LogManager.getLogger(EventStoreRequestHandlerBridge.class);

    public EventStoreRequestHandlerBridge(BusLifecycle lifecycle,
                                          EventStore eventStore,
                                          @Value("${evento.es.fetch.concurrency:4}")
                                          int fetchConcurrency,
                                          @Value("${evento.es.fetch.max.age.ms:30000}")
                                          long fetchMaxAgeMs) {
        var codec = new AdminPayloadCodec();
        int permits = Math.max(1, fetchConcurrency);
        // Bounds concurrent EventFetchRequest handlers so a fleet of (re)connecting
        // consumers cannot stampede the broker into heap exhaustion. Each fetch
        // transiently pins a large result set + deserialized event graph + encoded
        // response on heap; permits limit how many of those overlap.
        var fetchPermits = new Semaphore(permits, true);
        logger.info("EventFetchRequest concurrency limit: {} max-age: {}ms", permits, fetchMaxAgeMs);

        lifecycle.registerLocalHandler(EventFetchRequest.class.getSimpleName(), payload -> {
            var eventoRequest = codec.decodeRequest(payload);
            fetchPermits.acquire();
            try {
                // Staleness gate, checked AFTER the permit is acquired because the queue
                // wait is where age accumulates. The consumer abandons a fetch after its
                // client-side timeout (default 30s) and immediately retries, so serving a
                // request older than that does the full result-set + encode work for a
                // reply nobody is waiting on — under memory pressure that retry storm
                // repeats the heaviest work exactly when the broker can least afford it
                // (incident 2026-07-27). Timestamps are client clocks; a 0/negative value
                // (older client) disables the gate for that request, and fetchMaxAgeMs<=0
                // disables it globally.
                var now = System.currentTimeMillis();
                if (isExpired(eventoRequest.getTimestamp(), now, fetchMaxAgeMs)) {
                    throw new IllegalStateException(
                            "EventFetchRequest expired: age=" + (now - eventoRequest.getTimestamp())
                                    + "ms > max=" + fetchMaxAgeMs
                                    + "ms; the requesting consumer has already timed out, dropping stale fetch");
                }
                var fetchReq = (EventFetchRequest) eventoRequest.getBody();
                var entries = eventStore.fetchEvents(
                        fetchReq.getContext(),
                        fetchReq.getLastSequenceNumber(),
                        fetchReq.getLimit());
                var published = entries.stream()
                        .map(com.evento.server.es.eventstore.EventStoreEntry::toPublishedEvent)
                        .collect(Collectors.toCollection(ArrayList::new));
                var resp = new EventoResponse();
                resp.setCorrelationId(eventoRequest.getCorrelationId());
                resp.setBody(new EventFetchResponse(published));
                resp.setTimestamp(System.currentTimeMillis());
                return codec.encodeResponse(resp);
            } finally {
                fetchPermits.release();
            }
        });

        lifecycle.registerLocalHandler(EventLastSequenceNumberRequest.class.getSimpleName(), payload -> {
            var eventoRequest = codec.decodeRequest(payload);
            var resp = new EventoResponse();
            resp.setCorrelationId(eventoRequest.getCorrelationId());
            resp.setBody(new EventLastSequenceNumberResponse(eventStore.getLastEventSequenceNumber()));
            resp.setTimestamp(System.currentTimeMillis());
            return codec.encodeResponse(resp);
        });
    }

    /**
     * True when a request stamped at {@code requestTimestampMs} is older than {@code maxAgeMs}
     * at {@code nowMs}. A non-positive timestamp (client that doesn't stamp requests) or a
     * non-positive max age disables the check.
     */
    static boolean isExpired(long requestTimestampMs, long nowMs, long maxAgeMs) {
        if (maxAgeMs <= 0 || requestTimestampMs <= 0) return false;
        return nowMs - requestTimestampMs > maxAgeMs;
    }
}
