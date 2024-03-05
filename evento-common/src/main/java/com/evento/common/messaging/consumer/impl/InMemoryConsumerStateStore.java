package com.evento.common.messaging.consumer.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.messaging.consumer.ConsumerStateStore;
import com.evento.common.messaging.consumer.StoredSagaState;
import com.evento.common.modeling.state.SagaState;
import com.evento.common.performance.PerformanceService;
import com.evento.common.serialization.ObjectMapperUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The InMemoryConsumerStateStore class is an implementation of the ConsumerStateStore abstract class.
 * It represents an in-memory storage for consumer state information.
 */
public class InMemoryConsumerStateStore extends ConsumerStateStore {

    private final Map<Object, Lock> lockRegistry = new ConcurrentHashMap<>();
    private final Map<String, Long> lastEventSequenceNumberRepository = new ConcurrentHashMap<>();

    private final Map<Long, Map.Entry<String,SagaState>> sagaStateRepository = new ConcurrentHashMap<>();

	private final AtomicInteger sagaCounter = new AtomicInteger(1);

    /**
	 * Constructs a new InMemoryConsumerStateStore object.
	 *
	 * @param eventoServer         the EventoServer used for sending and receiving messages in an Evento cluster
	 * @param performanceService   the PerformanceService used for performance monitoring
	 */
	public InMemoryConsumerStateStore(EventoServer eventoServer,
                                      PerformanceService performanceService) {
        this(eventoServer, performanceService, ObjectMapperUtils.getPayloadObjectMapper(), Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
	 * This class represents an in-memory implementation of the ConsumerStateStore interface.
	 * It stores the state of the consumer such as last event sequence number and saga state.
     * @param eventoServer an evento server connection instance
     * @param performanceService a performance service connection instance
     * @param objectMapper an object mapper to convert messages
     * @param observerExecutor an executor for observers
     */
	public InMemoryConsumerStateStore(EventoServer eventoServer,
									  PerformanceService performanceService, ObjectMapper objectMapper,
									  Executor observerExecutor) {
        super(eventoServer, performanceService, objectMapper, observerExecutor);
    }

    /**
	 * Removes the saga state associated with the given saga ID.
	 *
	 * @param sagaId the ID of the saga
	 */
	@Override
    protected void removeSagaState(Long sagaId) {
        sagaStateRepository.remove(sagaId);
    }

    /**
	 * Leaves the exclusive zone for the specified consumer.
	 *
	 * @param consumerId the ID of the consumer leaving the exclusive zone
	 */
	@Override
    protected void leaveExclusiveZone(String consumerId) {
        obtain(consumerId).unlock();
    }

    /**
	 * Attempts to enter the exclusive zone for the specified consumer.
	 *
	 * @param consumerId the ID of the consumer entering the exclusive zone
	 * @return true if the consumer successfully enters the exclusive zone, false otherwise
	 */
	@Override
    protected boolean enterExclusiveZone(String consumerId) {
        return obtain(consumerId).tryLock();
    }

    /**
	 * Obtains a lock from the lock registry based on the provided lock key.
	 * If the lock does not exist in the lock registry, a new ReentrantLock will be created and added to the registry.
	 *
	 * @param lockKey the key of the lock in the lock registry
	 * @return the lock associated with the provided lock key
	 */
	protected synchronized Lock obtain(Object lockKey) {
        if (!lockRegistry.containsKey(lockKey))
            lockRegistry.put(lockKey, new ReentrantLock());
        return lockRegistry.get(lockKey);
    }


	/**
	 * Retrieves the last event sequence number for the specified consumer.
	 *
	 * @param consumerId the ID of the consumer for which to retrieve the last event sequence number
	 * @return the last event sequence number for the specified consumer, or null if the consumer ID is not found
	 */
	@Override
    protected Long getLastEventSequenceNumber(String consumerId) {
        return lastEventSequenceNumberRepository.getOrDefault(consumerId, null);
    }

    /**
	 * Sets the last event sequence number for the specified consumer.
	 *
	 * @param consumerId           the ID of the consumer for which to set the last event sequence number
	 * @param eventSequenceNumber  the last event sequence number to set for the consumer
	 */
	@Override
    protected void setLastEventSequenceNumber(String consumerId, Long eventSequenceNumber) {
        lastEventSequenceNumberRepository.put(consumerId, eventSequenceNumber);
    }


	/**
	 * Retrieves the StoredSagaState associated with the specified saga name, association property, and association value.
	 *
	 * @param sagaName           the name of the saga
	 * @param associationProperty the property used for association
	 * @param associationValue    the value of the association property
	 * @return the StoredSagaState associated with the specified saga name, association property, and association value. If no match is found, null is returned.
	 */
	@Override
    protected StoredSagaState getSagaState(String sagaName,
                                           String associationProperty,
                                           String associationValue) {
        return sagaStateRepository.entrySet()
				.stream().filter(s->s.getValue().getKey().equals(sagaName))
				.filter(s -> Objects.equals(associationValue,s.getValue().getValue().getAssociation(associationProperty)))
				.findFirst().map(s -> new StoredSagaState(s.getKey(), s.getValue().getValue()))
				.orElseGet(() ->  new StoredSagaState(null, null));
    }

    /**
	 * Sets the saga state for the given saga ID and saga name.
	 *
	 * @param id        the ID of the saga
	 * @param sagaName  the name of the saga
	 * @param sagaState the saga state to set for the saga
	 */
	@Override
    protected void setSagaState(Long id, String sagaName, SagaState sagaState) {
        sagaStateRepository.put(Objects.requireNonNullElseGet(id,
						() -> (long) sagaCounter.getAndIncrement()),
                new Map.Entry<>() {
                    @Override
                    public String getKey() {
                        return sagaName;
                    }

                    @Override
                    public SagaState getValue() {
                        return sagaState;
                    }

                    @Override
                    public SagaState setValue(SagaState value) {
                        return sagaState;
                    }
                });
    }
}
