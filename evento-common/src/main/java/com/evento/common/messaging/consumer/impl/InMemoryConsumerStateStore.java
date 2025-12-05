package com.evento.common.messaging.consumer.impl;

import com.evento.common.messaging.consumer.DeadPublishedEvent;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerFetchStatusResponseMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.messaging.consumer.ConsumerStateStore;
import com.evento.common.messaging.consumer.StoredSagaState;
import com.evento.common.modeling.state.SagaState;
import com.evento.common.performance.PerformanceService;
import com.evento.common.serialization.ObjectMapperUtils;

import java.time.ZonedDateTime;
import java.util.*;
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

    // Error state repositories per consumer
    private final Map<String, Boolean> isInErrorRepository = new ConcurrentHashMap<>();
    private final Map<String, ZonedDateTime> errorStartAtRepository = new ConcurrentHashMap<>();
    private final Map<String, ZonedDateTime> lastErrorAtRepository = new ConcurrentHashMap<>();
    private final Map<String, Long> errorCountRepository = new ConcurrentHashMap<>();
    private final Map<String, String> errorRepository = new ConcurrentHashMap<>();

    private final Map<Long, Map.Entry<String, SagaState>> sagaStateRepository = new ConcurrentHashMap<>();

    private final AtomicInteger sagaCounter = new AtomicInteger(1);

    private final ArrayList<DeadPublishedEvent> deadEventQueue = new ArrayList<>();

    /**
     * Builder for InMemoryConsumerStateStore.
     * Use this builder to create instances of InMemoryConsumerStateStore with the desired configuration.
     */
    public static class Builder {
        // Required parameters
        private final EventoServer eventoServer;
        private final PerformanceService performanceService;
        
        // Optional parameters with default values
        private ObjectMapper objectMapper = ObjectMapperUtils.getPayloadObjectMapper();
        private Executor observerExecutor = Executors.newCachedThreadPool();
        private long timeoutMillis = 30000; // Default timeout: 30 seconds

        /**
         * Creates a new Builder with the required parameters.
         *
         * @param eventoServer       an instance of evento server connection
         * @param performanceService an instance of performance service
         */
        public Builder(
                EventoServer eventoServer,
                PerformanceService performanceService) {
            this.eventoServer = eventoServer;
            this.performanceService = performanceService;
        }

        /**
         * Sets the object mapper to use for serialization.
         *
         * @param objectMapper an object mapper to manage serialization
         * @return this builder for method chaining
         */
        public Builder withObjectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        /**
         * Sets the executor to use for observers.
         *
         * @param observerExecutor observer executor
         * @return this builder for method chaining
         */
        public Builder withObserverExecutor(Executor observerExecutor) {
            this.observerExecutor = observerExecutor;
            return this;
        }

        /**
         * Sets the timeout in milliseconds for event fetching operations.
         *
         * @param timeoutMillis timeout in milliseconds
         * @return this builder for method chaining
         */
        public Builder withTimeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        /**
         * Builds a new InMemoryConsumerStateStore with the configured parameters.
         *
         * @return a new InMemoryConsumerStateStore instance
         */
        public InMemoryConsumerStateStore build() {
            return new InMemoryConsumerStateStore(
                    eventoServer,
                    performanceService,
                    objectMapper,
                    observerExecutor,
                    timeoutMillis
            );
        }
    }

    /**
     * Creates a new Builder for InMemoryConsumerStateStore.
     *
     * @param eventoServer       an instance of evento server connection
     * @param performanceService an instance of performance service
     * @return a new Builder instance
     */
    public static Builder builder(
            EventoServer eventoServer,
            PerformanceService performanceService) {
        return new Builder(eventoServer, performanceService);
    }


    
    /**
     * Private constructor used by the Builder and deprecated constructors.
     * Use {@link #builder(EventoServer, PerformanceService)} to create instances.
     *
     * @param eventoServer       an instance of evento server connection
     * @param performanceService an instance of performance service
     * @param objectMapper       an object mapper to manage serialization
     * @param observerExecutor   observer executor
     * @param timeoutMillis      timeout in milliseconds for event fetching operations
     */
    private InMemoryConsumerStateStore(
            EventoServer eventoServer,
            PerformanceService performanceService,
            ObjectMapper objectMapper,
            Executor observerExecutor,
            long timeoutMillis) {
        super(eventoServer, performanceService, objectMapper, observerExecutor, timeoutMillis);
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
     * @param consumerId          the ID of the consumer for which to set the last event sequence number
     * @param eventSequenceNumber the last event sequence number to set for the consumer
     */
    @Override
    protected void setLastEventSequenceNumber(String consumerId, Long eventSequenceNumber) {
        lastEventSequenceNumberRepository.put(consumerId, eventSequenceNumber);
        // Reset error state upon successful progress
        isInErrorRepository.put(consumerId, false);
        errorCountRepository.put(consumerId, 0L);
    }

    @Override
    public void setLastError(String consumerId, Throwable error) throws Exception {
        var now = ZonedDateTime.now();
        var wasInError = isInErrorRepository.getOrDefault(consumerId, false);
        if (!wasInError) {
            errorStartAtRepository.put(consumerId, now);
        }
        lastErrorAtRepository.put(consumerId, now);
        var count = errorCountRepository.getOrDefault(consumerId, 0L) + 1;
        errorCountRepository.put(consumerId, count);
        isInErrorRepository.put(consumerId, true);
        errorRepository.put(consumerId, getObjectMapper().writeValueAsString(new ExceptionWrapper(error)));
    }

    @Override
    public ConsumerFetchStatusResponseMessage toConsumerStatus(String consumerId) {
        var resp = new ConsumerFetchStatusResponseMessage();
        try {
            resp.setLastEventSequenceNumber(getLastEventSequenceNumberSagaOrHead(consumerId));
        } catch (Exception e) {
            // If we cannot compute it, default to 0
            resp.setLastEventSequenceNumber(0L);
        }
        try {
            resp.setDeadEvents(getEventsFromDeadEventQueue(consumerId));
        } catch (Exception e) {
            resp.setDeadEvents(Collections.emptyList());
        }
        resp.setInError(isInErrorRepository.getOrDefault(consumerId, false));
        resp.setErrorStartAt(errorStartAtRepository.get(consumerId));
        resp.setLastErrorAt(lastErrorAtRepository.get(consumerId));
        resp.setErrorCount(errorCountRepository.getOrDefault(consumerId, 0L));
        resp.setError(errorRepository.get(consumerId));
        return resp;
    }

    /**
	 * Adds a published event to the dead event queue.
	 *
	 * @param consumerId the ID of the consumer
	 * @param event the published event to be added
     */
	@Override
    public void addEventToDeadEventQueue(String consumerId, PublishedEvent event, Throwable exception) {
        deadEventQueue.add(new DeadPublishedEvent(
                consumerId,
                event.getEventName(),
                event.getAggregateId(),
                event.getEventMessage().getContext(),
                event.getEventSequenceNumber().toString(),
                event,
                false,
                new ExceptionWrapper(exception),
                ZonedDateTime.now()
        ));
    }

    /**
     * Removes the event from the dead event queue that matches the specified consumer ID and event sequence number.
     *
     * @param consumerId          the ID of the consumer
     * @param eventSequenceNumber the event sequence number
     */
	@Override
    public void removeEventFromDeadEventQueue(String consumerId, long eventSequenceNumber) {
        deadEventQueue.removeIf(de -> de.getConsumerId().equals(consumerId) && Long.parseLong(de.getEventSequenceNumber()) == eventSequenceNumber);
    }

    /**
	 * Retrieves a collection of events from the dead event queue that need to be reprocessed by a specific consumer.
	 *
	 * @param consumerId the ID of the consumer
	 * @return a collection of PublishedEvent objects to be reprocessed
     */
	@Override
    protected Collection<PublishedEvent> getEventsToReprocessFromDeadEventQueue(String consumerId) {
        return deadEventQueue.stream().filter(de -> de.getConsumerId().equals(consumerId))
                .filter(DeadPublishedEvent::isRetry).map(DeadPublishedEvent::getEvent).toList();
    }

    /**
	 * Retrieves a collection of events from the dead event queue that need to be reprocessed by a specific consumer.
	 *
	 * @param consumerId the ID of the consumer
	 * @return a collection of DeadPublishedEvent objects to be reprocessed
     */
	@Override
    public Collection<DeadPublishedEvent> getEventsFromDeadEventQueue(String consumerId) {
        return new ArrayList<>(deadEventQueue.stream().filter(de -> de.getConsumerId().equals(consumerId)).toList());
    }

    /**
	 * Sets the retry status of a dead event for a specific consumer.
	 *
	 * @param consumerId           the ID of the consumer
	 * @param eventSequenceNumber  the event sequence number
	 * @param retry                the retry status to be set (true for retry, false for no retry)
     */
	@Override
    public void setRetryDeadEvent(String consumerId, long eventSequenceNumber, boolean retry) {
        deadEventQueue.stream().filter(de -> de.getConsumerId().equals(consumerId) && Long.parseLong(de.getEventSequenceNumber()) == eventSequenceNumber)
                .findFirst().ifPresent(de -> de.setRetry(retry));
    }


    /**
     * Retrieves the StoredSagaState associated with the specified saga name, association property, and association value.
     *
     * @param sagaName            the name of the saga
     * @param associationProperty the property used for association
     * @param associationValue    the value of the association property
     * @return the StoredSagaState associated with the specified saga name, association property, and association value. If no match is found, null is returned.
     */
    @Override
    public StoredSagaState getSagaState(String sagaName,
                                           String associationProperty,
                                           String associationValue) {
        return sagaStateRepository.entrySet()
                .stream().filter(s -> s.getValue().getKey().equals(sagaName))
                .filter(s -> Objects.equals(associationValue, s.getValue().getValue().getAssociation(associationProperty)))
                .findFirst().map(s -> new StoredSagaState(s.getKey(), s.getValue().getValue()))
                .orElseGet(() -> new StoredSagaState(null, null));
    }

    /**
     * Returns a collection of StoredSagaState objects associated with the specified saga name.
     *
     * @param sagaName the name of the saga
     * @return a collection of StoredSagaState objects associated with the specified saga name.
     */
    @Override
    public Collection<StoredSagaState> getSagaStates(String sagaName) {
        return new ArrayList<>(sagaStateRepository.entrySet()
                .stream().filter(s -> s.getValue().getKey().equals(sagaName))
                .map(s -> new StoredSagaState(s.getKey(), s.getValue().getValue()))
                .toList());
    }

    /**
     * Sets the saga state for the given saga ID and saga name.
     *
     * @param id        the ID of the saga
     * @param sagaName  the name of the saga
     * @param sagaState the saga state to set for the saga
     */
    @Override
    public void setSagaState(Long id, String sagaName, SagaState sagaState) {
        sagaStateRepository.put(Objects.requireNonNullElseGet(id,
                        () -> ((long) sagaCounter.getAndIncrement())),
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
