package com.evento.test.broker;

import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.message.application.EventMessage;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.state.SerializedAggregateState;
import com.evento.server.es.BrokerEventStore;
import com.evento.server.es.AggregateStory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The event store of {@link EventoTestBroker}: one list in memory, numbered like CPES (1, 2, 3...,
 * in commit order). Appends are serialised, so the order consumers read is the order events
 * were stored, as on the real server.
 */
final class InMemoryEventStore implements BrokerEventStore {

    private final List<PublishedEvent> events = new ArrayList<>();
    private final ConcurrentHashMap<String, SerializedAggregateState<?>> snapshots = new ConcurrentHashMap<>();

    @Override
    public synchronized AggregateStory fetchAggregateStory(String aggregateId, boolean invalidateCaches,
                                                           boolean invalidateSnapshot) {
        var story = events.stream()
                .filter(e -> aggregateId.equals(e.getAggregateId()))
                .filter(e -> e.getEventMessage() instanceof DomainEventMessage)
                .map(e -> (DomainEventMessage) e.getEventMessage())
                .toList();
        SerializedAggregateState<?> snapshot = invalidateSnapshot ? null : snapshots.get(aggregateId);
        return new AggregateStory(snapshot == null ? new SerializedAggregateState<>(null) : snapshot, story);
    }

    @Override
    public synchronized long publishEvent(EventMessage<?> eventMessage, String aggregateId) {
        var event = new PublishedEvent();
        event.setEventSequenceNumber(events.size() + 1L);
        event.setAggregateId(aggregateId);
        event.setEventMessage(eventMessage);
        event.setEventName(eventMessage.getEventName());
        event.setCreatedAt(System.currentTimeMillis());
        events.add(event);
        return event.getEventSequenceNumber();
    }

    @Override
    public void saveSnapshot(String aggregateId, Long seqNum, SerializedAggregateState<?> state) {
        snapshots.put(aggregateId, state);
    }

    @Override
    public synchronized void deleteAggregate(String aggregateId) {
        snapshots.remove(aggregateId);
    }

    synchronized long lastSequenceNumber() {
        return events.size();
    }

    synchronized List<PublishedEvent> all() {
        return List.copyOf(events);
    }

    /**
     * The events after {@code after} whose context matches {@code context}, as the server reads
     * them: {@code *} matches any run of characters, {@code null} every context.
     */
    synchronized ArrayList<PublishedEvent> fetch(String context, long after, int limit) {
        Pattern pattern = context == null ? null : glob(context);
        return events.stream()
                .filter(e -> e.getEventSequenceNumber() > after)
                .filter(e -> pattern == null || pattern.matcher(contextOf(e)).matches())
                .sorted(Comparator.comparingLong(PublishedEvent::getEventSequenceNumber))
                .limit(limit)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static String contextOf(PublishedEvent e) {
        var context = e.getEventMessage() == null ? null : e.getEventMessage().getContext();
        return context == null ? "default" : context;
    }

    private static Pattern glob(String context) {
        var regex = new StringBuilder();
        for (String part : context.split("\\*", -1)) {
            if (!regex.isEmpty()) regex.append(".*");
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString());
    }
}
