package com.evento.lab.support;

import com.evento.application.client.BundleClient;
import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.messaging.consumer.EventFetchRequest;
import com.evento.common.messaging.consumer.EventFetchResponse;
import com.evento.common.messaging.consumer.EventLastSequenceNumberRequest;
import com.evento.common.messaging.consumer.EventLastSequenceNumberResponse;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.message.application.ServiceEventMessage;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.common.modeling.messaging.payload.DomainEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * A test-only "event store bundle" that connects to the broker and handles
 * {@link EventFetchRequest} and {@link EventLastSequenceNumberRequest}.
 *
 * <p>Consumer engines inside an {@link com.evento.application.EventoBundle} that is
 * connected to the same broker will have their fetch requests routed here,
 * making this the in-process event journal for integration tests.
 */
public final class TestEventStoreBundleClient implements AutoCloseable {

    private final BundleClient client;
    private final AdminPayloadCodec codec = new AdminPayloadCodec();
    private final List<PublishedEvent> store = new CopyOnWriteArrayList<>();
    private final AtomicLong seqGen = new AtomicLong(0);

    public TestEventStoreBundleClient(int brokerPort) throws Exception {
        client = BundleClient.builder("test-event-store", "store-1")
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

    /**
     * Publish a domain event into the in-memory store. Returns the assigned sequence number.
     * The event becomes immediately visible to consumer engines fetching from this store.
     */
    public long publish(DomainEvent event, String aggregateId) {
        long seq = seqGen.incrementAndGet();
        var em = new DomainEventMessage(event);
        var pe = new PublishedEvent();
        pe.setEventSequenceNumber(seq);
        pe.setAggregateId(aggregateId);
        pe.setEventMessage(em);
        pe.setEventName(event.getClass().getSimpleName());
        pe.setCreatedAt(System.currentTimeMillis());
        store.add(pe);
        return seq;
    }

    /**
     * Publish a {@link PublishedEvent} whose {@link ServiceEventMessage} was constructed with a
     * null payload — simulating an old or corrupted DB record where {@code objectClass} was never
     * stored. The outer {@code eventName} is set explicitly so event-name-based routing still
     * works; only {@code ServiceEventMessage.getEventName()} is affected (returns {@code null}).
     *
     * <p>Used by regression tests for the fix in {@code Message.getPayloadName()}.
     */
    public long publishCorrupted(String eventName, String aggregateId) {
        long seq = seqGen.incrementAndGet();
        var em = new ServiceEventMessage(null); // null payload → objectClass = null
        var pe = new PublishedEvent();
        pe.setEventSequenceNumber(seq);
        pe.setAggregateId(aggregateId);
        pe.setEventMessage(em);
        pe.setEventName(eventName);
        pe.setCreatedAt(System.currentTimeMillis());
        store.add(pe);
        return seq;
    }

    /** Returns all events currently in the store (snapshot). */
    public List<PublishedEvent> allEvents() {
        return List.copyOf(store);
    }

    /** Current last sequence number (0 if no events have been published). */
    public long lastSequenceNumber() {
        return seqGen.get();
    }

    @Override
    public void close() {
        client.stop(Duration.ofSeconds(5));
    }
}
