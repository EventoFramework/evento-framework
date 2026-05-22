package com.evento.lab.ms.it.support;

import com.evento.application.client.v2.BundleClient;
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
import com.evento.common.modeling.messaging.payload.ServiceEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * A test-only "event store bundle" that connects to the ms broker and handles
 * {@link EventFetchRequest} and {@link EventLastSequenceNumberRequest}.
 */
public final class MsTestEventStore implements AutoCloseable {

    private final BundleClient client;
    private final AdminPayloadCodec codec = new AdminPayloadCodec();
    private final List<PublishedEvent> store = new CopyOnWriteArrayList<>();
    private final AtomicLong seqGen = new AtomicLong(0);
    private final ConcurrentHashMap<Long, String> eventContexts = new ConcurrentHashMap<>();

    public MsTestEventStore(int brokerPort) throws Exception {
        client = BundleClient.builder("ms-test-event-store", "ms-store-1")
                .host("127.0.0.1")
                .port(brokerPort)
                .bundleVersion("1")
                .handlerPayloadTypes(List.of("EventFetchRequest", "EventLastSequenceNumberRequest"))
                .build();

        client.registerRequestHandler("EventFetchRequest", (payload, ctx) -> {
            var req = (EventFetchRequest) codec.decodeRequest(payload).getBody();
            long from = req.getLastSequenceNumber();
            int limit = req.getLimit();
            String reqContext = req.getContext();
            var events = store.stream()
                    .filter(pe -> pe.getEventSequenceNumber() > from)
                    .filter(pe -> {
                        if (reqContext == null || "*".equals(reqContext)) return true;
                        String evtCtx = eventContexts.getOrDefault(pe.getEventSequenceNumber(), "*");
                        return "*".equals(evtCtx) || reqContext.equals(evtCtx);
                    })
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
     * Publish a domain event with a context tag. Returns the assigned sequence number.
     */
    public long publishWithContext(DomainEvent event, String aggregateId, String context) {
        long seq = seqGen.incrementAndGet();
        var em = new DomainEventMessage(event);
        var pe = new PublishedEvent();
        pe.setEventSequenceNumber(seq);
        pe.setAggregateId(aggregateId);
        pe.setEventMessage(em);
        pe.setEventName(event.getClass().getSimpleName());
        pe.setCreatedAt(System.currentTimeMillis());
        store.add(pe);
        eventContexts.put(seq, context);
        return seq;
    }

    /**
     * Publish a service event into the in-memory store. Returns the assigned sequence number.
     */
    public long publishServiceEvent(ServiceEvent event) {
        long seq = seqGen.incrementAndGet();
        var em = new ServiceEventMessage(event);
        var pe = new PublishedEvent();
        pe.setEventSequenceNumber(seq);
        pe.setEventMessage(em);
        pe.setEventName(event.getClass().getSimpleName());
        pe.setCreatedAt(System.currentTimeMillis());
        store.add(pe);
        return seq;
    }

    /**
     * Publish a service event with a context tag. Returns the assigned sequence number.
     */
    public long publishServiceEventWithContext(ServiceEvent event, String context) {
        long seq = seqGen.incrementAndGet();
        var em = new ServiceEventMessage(event);
        var pe = new PublishedEvent();
        pe.setEventSequenceNumber(seq);
        pe.setEventMessage(em);
        pe.setEventName(event.getClass().getSimpleName());
        pe.setCreatedAt(System.currentTimeMillis());
        store.add(pe);
        eventContexts.put(seq, context);
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
