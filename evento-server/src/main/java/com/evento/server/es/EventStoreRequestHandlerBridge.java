package com.evento.server.es;

import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.messaging.consumer.EventFetchRequest;
import com.evento.common.messaging.consumer.EventFetchResponse;
import com.evento.common.messaging.consumer.EventLastSequenceNumberRequest;
import com.evento.common.messaging.consumer.EventLastSequenceNumberResponse;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.server.bus.lifecycle.BusLifecycle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Registers server-local handlers for event-store query requests so the broker
 * answers {@link EventFetchRequest} and {@link EventLastSequenceNumberRequest}
 * directly from the {@link EventStore} rather than forwarding them to a bundle.
 */
@Component
public class EventStoreRequestHandlerBridge {

    public EventStoreRequestHandlerBridge(BusLifecycle lifecycle, EventStore eventStore) {
        var codec = new AdminPayloadCodec();

        lifecycle.registerLocalHandler(EventFetchRequest.class.getSimpleName(), payload -> {
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