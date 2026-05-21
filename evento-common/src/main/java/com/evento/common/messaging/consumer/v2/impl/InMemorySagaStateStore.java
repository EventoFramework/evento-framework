package com.evento.common.messaging.consumer.v2.impl;

import com.evento.common.messaging.consumer.StoredSagaState;
import com.evento.common.messaging.consumer.v2.SagaStateStore;
import com.evento.common.modeling.state.SagaState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Single-JVM {@link SagaStateStore}. Stores instances in a list keyed by
 * saga name, and walks the {@link SagaState} associations map to satisfy
 * {@link #findByAssociation}.
 */
public final class InMemorySagaStateStore implements SagaStateStore {

    private record Row(long id, String sagaName, SagaState state) {}

    private final AtomicLong idSeq = new AtomicLong(0L);
    private final Map<Long, Row> byId = new HashMap<>();

    @Override
    public synchronized Optional<StoredSagaState> findByAssociation(String sagaName,
                                                                    String associationProperty,
                                                                    String associationValue) {
        for (Row r : byId.values()) {
            if (!r.sagaName.equals(sagaName) || r.state == null) continue;
            String stored = r.state.getAssociation(associationProperty);
            if (associationValue.equals(stored)) {
                return Optional.of(new StoredSagaState(r.id, r.state));
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized Collection<StoredSagaState> findAll(String sagaName) {
        List<StoredSagaState> out = new ArrayList<>();
        for (Row r : byId.values()) {
            if (r.sagaName.equals(sagaName)) {
                out.add(new StoredSagaState(r.id, r.state));
            }
        }
        return out;
    }

    @Override
    public synchronized long insert(String sagaName, SagaState state) {
        long id = idSeq.incrementAndGet();
        byId.put(id, new Row(id, sagaName, state));
        return id;
    }

    @Override
    public synchronized void update(long id, SagaState state) {
        Row existing = byId.get(id);
        if (existing == null) {
            throw new IllegalArgumentException("no saga state with id=" + id);
        }
        byId.put(id, new Row(id, existing.sagaName, state));
    }

    @Override
    public synchronized void delete(long id) {
        byId.remove(id);
    }
}
