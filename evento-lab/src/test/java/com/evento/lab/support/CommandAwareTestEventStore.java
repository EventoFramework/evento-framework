package com.evento.lab.support;

import com.evento.application.client.v2.BundleClient;
import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.messaging.consumer.EventFetchRequest;
import com.evento.common.messaging.consumer.EventFetchResponse;
import com.evento.common.messaging.consumer.EventLastSequenceNumberRequest;
import com.evento.common.messaging.consumer.EventLastSequenceNumberResponse;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.message.application.EventMessage;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.modeling.state.SerializedAggregateState;
import com.evento.server.es.AggregateStory;
import com.evento.server.es.BrokerEventStore;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Unified in-memory event store for command-flow integration tests.
 *
 * <p>Implements {@link BrokerEventStore} so it can be passed to
 * {@link com.evento.server.es.CommandBrokerHandler}, AND connects to the
 * embedded broker as a {@link BundleClient} to serve
 * {@code EventFetchRequest} / {@code EventLastSequenceNumberRequest} so
 * projector consumer engines can poll for events.
 *
 * <p>All writes ({@link #publishEvent} from the broker handler, and
 * {@link #publish} from tests) land in the same in-memory list, so the
 * full command → event-store → projector loop closes in-process.
 */
public final class CommandAwareTestEventStore implements BrokerEventStore, AutoCloseable {

    private final BundleClient client;
    private final AdminPayloadCodec codec = new AdminPayloadCodec();
    private final List<PublishedEvent> store = new CopyOnWriteArrayList<>();
    private final AtomicLong seqGen = new AtomicLong(0);
    private final ConcurrentHashMap<String, SerializedAggregateState<?>> snapshots = new ConcurrentHashMap<>();

    public CommandAwareTestEventStore(int brokerPort) throws Exception {
        client = BundleClient.builder("lab-event-store", "store-1")
                .host("127.0.0.1")
                .port(brokerPort)
                .bundleVersion("1")
                .handlerPayloadTypes(List.of("EventFetchRequest", "EventLastSequenceNumberRequest"))
                .build();

        client.registerRequestHandler("EventFetchRequest", (payload, ctx) -> {
            var req = (EventFetchRequest) codec.decodeRequest(payload).getBody();
            long from = req.getLastSequenceNumber();
            int limit = req.getLimit();
            var events = store.stream()
                    .filter(pe -> pe.getEventSequenceNumber() > from)
                    .sorted(Comparator.comparingLong(PublishedEvent::getEventSequenceNumber))
                    .limit(limit)
                    .collect(Collectors.toCollection(ArrayList::new));
            var resp = new EventoResponse();
            resp.setBody(new EventFetchResponse(events));
            return codec.encodeResponse(resp);
        });

        client.registerRequestHandler("EventLastSequenceNumberRequest", (payload, ctx) -> {
            var r = new EventLastSequenceNumberResponse();
            r.setNumber(seqGen.get());
            var resp = new EventoResponse();
            resp.setBody(r);
            return codec.encodeResponse(resp);
        });

        client.start().get(10, TimeUnit.SECONDS);
    }

    // ---- BrokerEventStore ----

    @Override
    public AggregateStory fetchAggregateStory(String aggregateId,
                                              boolean invalidateCaches,
                                              boolean invalidateSnapshot) {
        var events = store.stream()
                .filter(pe -> aggregateId.equals(pe.getAggregateId()))
                .filter(pe -> pe.getEventMessage() instanceof DomainEventMessage)
                .sorted(Comparator.comparingLong(PublishedEvent::getEventSequenceNumber))
                .map(pe -> (DomainEventMessage) pe.getEventMessage())
                .toList();

        SerializedAggregateState<?> snapshot = invalidateSnapshot ? null : snapshots.get(aggregateId);
        if (snapshot == null) {
            snapshot = new SerializedAggregateState<>(null);
        }
        return new AggregateStory(snapshot, events);
    }

    @Override
    public long publishEvent(EventMessage<?> eventMessage, String aggregateId) {
        long seq = seqGen.incrementAndGet();
        var pe = new PublishedEvent();
        pe.setEventSequenceNumber(seq);
        pe.setAggregateId(aggregateId);
        pe.setEventMessage(eventMessage);
        pe.setEventName(eventMessage.getEventName());
        pe.setCreatedAt(System.currentTimeMillis());
        store.add(pe);
        return seq;
    }

    @Override
    public void saveSnapshot(String aggregateId, Long seqNum, SerializedAggregateState<?> state) {
        snapshots.put(aggregateId, state);
    }

    @Override
    public void deleteAggregate(String aggregateId) {
        // no-op in tests
    }

    // ---- Test helpers ----

    public long publish(DomainEvent event, String aggregateId) {
        var em = new DomainEventMessage(event);
        long seq = seqGen.incrementAndGet();
        var pe = new PublishedEvent();
        pe.setEventSequenceNumber(seq);
        pe.setAggregateId(aggregateId);
        pe.setEventMessage(em);
        pe.setEventName(event.getClass().getSimpleName());
        pe.setCreatedAt(System.currentTimeMillis());
        store.add(pe);
        return seq;
    }

    public List<PublishedEvent> allEvents() {
        return List.copyOf(store);
    }

    public long lastSequenceNumber() {
        return seqGen.get();
    }

    @Override
    public void close() {
        client.stop(Duration.ofSeconds(5));
    }
}
