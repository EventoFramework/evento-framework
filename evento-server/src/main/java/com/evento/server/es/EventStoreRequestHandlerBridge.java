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
                                          int fetchConcurrency) {
        var codec = new AdminPayloadCodec();
        int permits = Math.max(1, fetchConcurrency);
        // Bounds concurrent EventFetchRequest handlers so a fleet of (re)connecting
        // consumers cannot stampede the broker into heap exhaustion. Each fetch
        // transiently pins a large result set + deserialized event graph + encoded
        // response on heap; permits limit how many of those overlap.
        var fetchPermits = new Semaphore(permits, true);
        logger.info("EventFetchRequest concurrency limit: {}", permits);

        lifecycle.registerLocalHandler(EventFetchRequest.class.getSimpleName(), payload -> {
            fetchPermits.acquire();
            try {
                var eventoRequest = codec.decodeRequest(payload);
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
}
