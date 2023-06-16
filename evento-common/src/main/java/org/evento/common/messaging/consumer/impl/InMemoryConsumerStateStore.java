package org.evento.common.messaging.consumer.impl;

import org.evento.common.messaging.consumer.StoredSagaState;
import org.evento.common.modeling.state.SagaState;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.messaging.consumer.ConsumerStateStore;
import org.evento.common.performance.PerformanceService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class InMemoryConsumerStateStore extends ConsumerStateStore {

    private final Map<Object, Lock> lockRegistry = new HashMap<>();
    private final Map<String, Long> lastEventSequenceNumberRepository = new HashMap<>();

    private final Map<String, SagaState> sagaStateRepository = new HashMap<>();

    public InMemoryConsumerStateStore(MessageBus messageBus, String bundleId, String serverNodeName, PerformanceService performanceService) {
        super(messageBus, bundleId, serverNodeName, performanceService);
    }

    @Override
    protected void removeSagaState(Long sagaId) throws Exception {

    }

    @Override
    protected void leaveExclusiveZone(String consumerId) {
        obtain(consumerId).unlock();
    }

    @Override
    protected boolean enterExclusiveZone(String consumerId) {
        return obtain(consumerId).tryLock();
    }

    protected synchronized Lock obtain(Object lockKey) {
        if(!lockRegistry.containsKey(lockKey))
            lockRegistry.put(lockKey, new ReentrantLock());
        return lockRegistry.get(lockKey);
    }

    @Override
    protected Long getLastEventSequenceNumber(String consumerId) {
        return lastEventSequenceNumberRepository.getOrDefault(consumerId, 0L);
    }

    @Override
    protected void setLastEventSequenceNumber(String consumerId, Long eventSequenceNumber) {
        lastEventSequenceNumberRepository.put(consumerId, eventSequenceNumber);
    }

    @Override
    protected StoredSagaState getSagaState(String sagaName,
										   String associationProperty,
										   String associationValue) {
        return null;
    }

    @Override
    protected void setSagaState(Long id, String sagaName, SagaState sagaState) {
      
    }
}
