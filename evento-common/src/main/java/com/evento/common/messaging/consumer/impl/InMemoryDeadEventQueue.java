package com.evento.common.messaging.consumer.impl;

import com.evento.common.messaging.consumer.DeadPublishedEvent;
import com.evento.common.messaging.consumer.DeadEventQueue;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.dto.PublishedEvent;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-JVM {@link DeadEventQueue}. One bucket per consumer id; entries are
 * keyed by {@link PublishedEvent#getEventSequenceNumber()} within the bucket.
 */
public final class InMemoryDeadEventQueue implements DeadEventQueue {

    private static final class Entry {
        final PublishedEvent event;
        final ExceptionWrapper exception;
        final ZonedDateTime deadAt;
        boolean retry;

        Entry(PublishedEvent event, ExceptionWrapper exception, ZonedDateTime deadAt) {
            this.event = event;
            this.exception = exception;
            this.deadAt = deadAt;
            this.retry = false;
        }
    }

    private final Map<String, Map<Long, Entry>> byConsumer = new HashMap<>();
    private final Clock clock;

    public InMemoryDeadEventQueue() { this(Clock.systemUTC()); }
    public InMemoryDeadEventQueue(Clock clock) { this.clock = clock; }

    @Override
    public synchronized void add(String consumerId, PublishedEvent event, Throwable cause) {
        var bucket = byConsumer.computeIfAbsent(consumerId, c -> new HashMap<>());
        bucket.put(event.getEventSequenceNumber(),
                new Entry(event, new ExceptionWrapper(cause), ZonedDateTime.ofInstant(clock.instant(), ZoneId.systemDefault())));
    }

    @Override
    public synchronized void remove(String consumerId, long eventSequenceNumber) {
        var bucket = byConsumer.get(consumerId);
        if (bucket != null) bucket.remove(eventSequenceNumber);
    }

    @Override
    public synchronized Collection<PublishedEvent> getRetriable(String consumerId) {
        var bucket = byConsumer.get(consumerId);
        if (bucket == null) return List.of();
        List<PublishedEvent> out = new ArrayList<>();
        for (Entry e : bucket.values()) {
            if (e.retry) out.add(e.event);
        }
        return out;
    }

    @Override
    public synchronized Collection<DeadPublishedEvent> getAll(String consumerId) {
        var bucket = byConsumer.get(consumerId);
        if (bucket == null) return List.of();
        List<DeadPublishedEvent> out = new ArrayList<>(bucket.size());
        for (Entry e : bucket.values()) {
            out.add(new DeadPublishedEvent(
                    consumerId,
                    e.event.getEventName(),
                    e.event.getAggregateId(),
                    e.event.getEventMessage().getContext(),
                    String.valueOf(e.event.getEventSequenceNumber()),
                    e.event,
                    e.retry,
                    e.exception,
                    e.deadAt));
        }
        return out;
    }

    @Override
    public synchronized void setRetry(String consumerId, long eventSequenceNumber, boolean retry) {
        var bucket = byConsumer.get(consumerId);
        if (bucket == null) return;
        Entry e = bucket.get(eventSequenceNumber);
        if (e != null) e.retry = retry;
    }
}
