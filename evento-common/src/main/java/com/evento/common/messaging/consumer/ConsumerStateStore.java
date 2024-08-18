package com.evento.common.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.state.SagaState;
import com.evento.common.performance.PerformanceService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The abstract class represents a state store for the consumer, which is responsible for consuming events and tracking the state of projections and sagas.
 */
public abstract class ConsumerStateStore {


    protected static final Logger logger = LogManager.getLogger(ConsumerStateStore.class);

    protected final EventoServer eventoServer;
    private final PerformanceService performanceService;
    private final ObjectMapper objectMapper;

    private final Executor observerExecutor;

    protected ConsumerStateStore(
            EventoServer eventoServer,
            PerformanceService performanceService,
            ObjectMapper objectMapper, Executor observerExecutor) {
        this.eventoServer = eventoServer;
        this.performanceService = performanceService;
        this.objectMapper = objectMapper;
        this.observerExecutor = observerExecutor;
    }

    /**
     * Consumes events for a projector.
     *
     * @param consumerId             the ID of the consumer
     * @param projectorName          the name of the projector
     * @param context                the context
     * @param projectorEventConsumer the projector event consumer
     * @param fetchSize              the number of events to fetch at a time
     * @return the number of events consumed
     * @throws Throwable if an error occurs during event consumption
     */
    public int consumeEventsForProjector(
            String consumerId,
            String projectorName,
            String context,
            EventConsumer projectorEventConsumer,
            int fetchSize) throws Throwable {
        var consumedEventCount = 0;
        if (enterExclusiveZone(consumerId)) {
            try {
                var lastEventSequenceNumber = getLastEventSequenceNumber(consumerId);
                if (lastEventSequenceNumber == null) lastEventSequenceNumber = 0L;

                var resp = ((EventFetchResponse) eventoServer.request(
                        new EventFetchRequest(
                                context,
                                lastEventSequenceNumber,
                                fetchSize,
                                projectorName)).get());
                for (PublishedEvent event : resp.getEvents()) {
                    var start = Instant.now();
                    try {
                        projectorEventConsumer.consume(event);
                    } catch (Exception e) {
                        addEventToDeadEventQueue(consumerId, event, e);
                        logger.error("Event consumption Error for projection %s and event %s after retry policy. Event added to Dead Event Queue".formatted(projectorName, event.getEventName()), e);
                    }
                    setLastEventSequenceNumber(consumerId, event.getEventSequenceNumber());
                    consumedEventCount++;
                    performanceService.sendServiceTimeMetric(
                            eventoServer.getBundleId(),
                            eventoServer.getInstanceId(),
                            projectorName,
                            event.getEventMessage(),
                            start,
                            event.getEventMessage().isForceTelemetry()
                    );
                }
            } finally {
                leaveExclusiveZone(consumerId);
            }
        } else {
            return -1;
        }
        return consumedEventCount;

    }

    /**
     * Consumes dead events for a projector.
     *
     * @param consumerId             the ID of the consumer
     * @param projectorName          the name of the projector
     * @param projectorEventConsumer the projector event consumer
     * @throws Exception if an error occurs during event consumption
     */
    public void consumeDeadEventsForProjector(
            String consumerId,
            String projectorName,
            EventConsumer projectorEventConsumer) throws Exception{
        if (enterExclusiveZone(consumerId)) {
            try {
                var events = getEventsToReprocessFromDeadEventQueue(consumerId);
                for (PublishedEvent event : events) {
                    var start = Instant.now();
                    try {
                        removeEventFromDeadEventQueue(consumerId, event);
                        projectorEventConsumer.consume(event);
                    } catch (Exception e) {
                        addEventToDeadEventQueue(consumerId, event, e);
                        logger.error("Event consumption Error for projection %s and event %s after retry policy. Event added to Dead Event Queue".formatted(projectorName, event.getEventName()), e);
                    }
                    performanceService.sendServiceTimeMetric(
                            eventoServer.getBundleId(),
                            eventoServer.getInstanceId(),
                            projectorName,
                            event.getEventMessage(),
                            start,
                            event.getEventMessage().isForceTelemetry()
                    );
                }
            } finally {
                leaveExclusiveZone(consumerId);
            }
        }
    }


    /**
     * Consumes events for an observer.
     *
     * @param consumerId            the ID of the consumer
     * @param observerName          the name of the observer
     * @param context               the context
     * @param observerEventConsumer the observer event consumer
     * @param fetchSize             the number of events to fetch at a time
     * @return the number of events consumed
     * @throws Throwable if an error occurs during event consumption
     */
    public int consumeEventsForObserver(
            String consumerId,
            String observerName,
            String context,
            EventConsumer observerEventConsumer,
            int fetchSize) throws Throwable {
        var consumedEventCount = 0;
        if (enterExclusiveZone(consumerId)) {
            try {
                var lastEventSequenceNumber = getLastEventSequenceNumberSagaOrHead(consumerId);
                var resp = ((EventFetchResponse) eventoServer.request(
                        new EventFetchRequest(context, lastEventSequenceNumber, fetchSize, observerName)).get());
                for (PublishedEvent event : resp.getEvents()) {
                    var start = Instant.now();
                    observerExecutor.execute(() -> {
                        try {
                            observerEventConsumer.consume(event);
                        } catch (Exception e) {
                            try {
                                addEventToDeadEventQueue(consumerId, event, e);
                                logger.error("Event consumption Error for consumer %s and event %s after retry policy. Event added to Dead Event Queue".formatted(observerName, event.getEventName()), e);
                            } catch (Exception ex) {
                                logger.error("Dead event queue insert failed for consumer %s and event %s. Will be ignored".formatted(observerName, event.getEventName()));
                            }

                        }
                    });
                    setLastEventSequenceNumber(consumerId, event.getEventSequenceNumber());
                    consumedEventCount++;
                    performanceService.sendServiceTimeMetric(
                            eventoServer.getBundleId(),
                            eventoServer.getInstanceId(),
                            observerName,
                            event.getEventMessage(),
                            start,
                            event.getEventMessage().isForceTelemetry()
                    );
                }
            } finally {
                leaveExclusiveZone(consumerId);
            }
        } else {
            return -1;
        }
        return consumedEventCount;

    }


    /**
     * Consumes dead events for an observer.
     *
     * @param consumerId             the ID of the consumer
     * @param observerName          the name of the observer
     * @param observerEventConsumer the event consumer for the observer
     * @throws Exception if an error occurs during event consumption
     */
    public void consumeDeadEventsForObserver(
            String consumerId,
            String observerName,
            EventConsumer observerEventConsumer) throws Exception {
        if (enterExclusiveZone(consumerId)) {
            try {
                var events = getEventsToReprocessFromDeadEventQueue(consumerId);
                for (PublishedEvent event : events) {
                    var start = Instant.now();
                    observerExecutor.execute(() -> {
                        try {
                            removeEventFromDeadEventQueue(consumerId, event);
                            observerEventConsumer.consume(event);
                        } catch (Exception e) {
                            try {
                                addEventToDeadEventQueue(consumerId, event, e);
                                logger.error("Event consumption Error for consumer %s and event %s after retry policy. Event added to Dead Event Queue".formatted(observerName, event.getEventName()), e);
                            } catch (Exception ex) {
                                logger.error("Dead event queue insert failed for consumer %s and event %s. Will be ignored".formatted(observerName, event.getEventName()));
                            }

                        }
                    });
                    setLastEventSequenceNumber(consumerId, event.getEventSequenceNumber());
                    performanceService.sendServiceTimeMetric(
                            eventoServer.getBundleId(),
                            eventoServer.getInstanceId(),
                            observerName,
                            event.getEventMessage(),
                            start,
                            event.getEventMessage().isForceTelemetry()
                    );
                }
            } finally {
                leaveExclusiveZone(consumerId);
            }
        }

    }

    /**
     * Consumes events for a saga.
     *
     * @param consumerId        the ID of the consumer
     * @param sagaName          the name of the saga
     * @param context           the context
     * @param sagaEventConsumer the saga event consumer
     * @param fetchSize         the number of events to fetch at a time
     * @return the number of events consumed
     * @throws Throwable if an error occurs during event consumption
     */
    public int consumeEventsForSaga(String consumerId, String sagaName,
                                    String context,
                                    SagaEventConsumer sagaEventConsumer,
                                    int fetchSize) throws Throwable {
        var consumedEventCount = 0;
        if (enterExclusiveZone(consumerId)) {
            try {
                var lastEventSequenceNumber = getLastEventSequenceNumberSagaOrHead(consumerId);
                var resp = ((EventFetchResponse) eventoServer.request(
                        new EventFetchRequest(context, lastEventSequenceNumber, fetchSize, sagaName)).get());
                for (PublishedEvent event : resp.getEvents()) {
                    var start = Instant.now();
                    var sagaStateId = new AtomicReference<Long>();
                    try {
                        var newState = sagaEventConsumer.consume((name, associationProperty, associationValue) -> {
                            var state = getSagaState(name, associationProperty, associationValue);
                            sagaStateId.set(state.getId());
                            return state.getState();
                        }, event);
                        if (newState != null) {
                            if (newState.isEnded()) {
                                removeSagaState(sagaStateId.get());
                            } else {
                                setSagaState(sagaStateId.get(), sagaName, newState);
                            }
                        }
                    } catch (Exception e) {
                        addEventToDeadEventQueue(consumerId, event, e);
                        logger.error("Event consumption Error for consumer %s and saga %s after retry policy. Event added to Dead Event Queue".formatted(sagaName, event.getEventName()), e);
                    }
                    setLastEventSequenceNumber(consumerId, event.getEventSequenceNumber());
                    consumedEventCount++;
                    performanceService.sendServiceTimeMetric(
                            eventoServer.getBundleId(),
                            eventoServer.getInstanceId(),
                            sagaName,
                            event.getEventMessage(),
                            start,
                            event.getEventMessage().isForceTelemetry()
                    );
                }
            } finally {
                leaveExclusiveZone(consumerId);
            }
        } else {
            return -1;
        }
        return consumedEventCount;
    }

    /**
     * Consume dead events for a saga.
     *
     * @param consumerId The ID of the consumer processing the dead events.
     * @param sagaName The name of the saga.
     * @param sagaEventConsumer The implementation of the SagaEventConsumer interface that will consume the dead events.
     *
     */
    public void consumeDeadEventsForSaga(
            String consumerId, String sagaName,
            SagaEventConsumer sagaEventConsumer) throws Exception {
        if (enterExclusiveZone(consumerId)) {
            try {
                var events = getEventsToReprocessFromDeadEventQueue(consumerId);
                for (PublishedEvent event : events) {
                    var start = Instant.now();
                    var sagaStateId = new AtomicReference<Long>();
                    try {
                        removeEventFromDeadEventQueue(consumerId, event);
                        var newState = sagaEventConsumer.consume((name, associationProperty, associationValue) -> {
                            var state = getSagaState(name, associationProperty, associationValue);
                            sagaStateId.set(state.getId());
                            return state.getState();
                        }, event);
                        if (newState != null) {
                            if (newState.isEnded()) {
                                removeSagaState(sagaStateId.get());
                            } else {
                                setSagaState(sagaStateId.get(), sagaName, newState);
                            }
                        }
                    } catch (Exception e) {
                        addEventToDeadEventQueue(consumerId, event, e);
                        logger.error("Event consumption Error for consumer %s and saga %s after retry policy. Event added to Dead Event Queue".formatted(sagaName, event.getEventName()), e);
                    }
                    setLastEventSequenceNumber(consumerId, event.getEventSequenceNumber());
                    performanceService.sendServiceTimeMetric(
                            eventoServer.getBundleId(),
                            eventoServer.getInstanceId(),
                            sagaName,
                            event.getEventMessage(),
                            start,
                            event.getEventMessage().isForceTelemetry()
                    );
                }
            } finally {
                leaveExclusiveZone(consumerId);
            }
        }

    }


    /**
     * Retrieves the last event sequence number for a saga or head consumer.
     *
     * @param consumerId the ID of the consumer
     * @return the last event sequence number for the consumer
     * @throws Exception if an error occurs
     */
    public long getLastEventSequenceNumberSagaOrHead(String consumerId) throws Exception {
        var last = getLastEventSequenceNumber(consumerId);
        if (last == null) {
            var head = ((EventLastSequenceNumberResponse) this.eventoServer.request(new EventLastSequenceNumberRequest()).get()).getNumber();
            setLastEventSequenceNumber(consumerId, head);
            return head;
        }
        return last;
    }

    /**
     * Removes the state of a saga identified by its ID.
     *
     * @param sagaId the ID of the saga
     * @throws Exception if an error occurs during state removal
     */
    protected abstract void removeSagaState(Long sagaId) throws Exception;

    /**
     * This method is called when a consumer leaves the exclusive zone.
     *
     * @param consumerId the ID of the consumer
     */
    protected abstract void leaveExclusiveZone(String consumerId);

    /**
     * This method is called when a consumer enters the exclusive zone.
     *
     * @param consumerId the ID of the consumer
     * @return true if the consumer successfully enters the exclusive zone, false otherwise
     */
    protected abstract boolean enterExclusiveZone(String consumerId);

    /**
     * Retrieves the last event sequence number for a given consumer.
     *
     * @param consumerId the ID of the consumer
     * @return the last event sequence number for the consumer, or null if not found
     * @throws Exception if an error occurs
     */
    protected abstract Long getLastEventSequenceNumber(String consumerId) throws Exception;

    /**
     * Sets the last event sequence number for a consumer.
     *
     * @param consumerId          the ID of the consumer
     * @param eventSequenceNumber the last event sequence number to be set
     * @throws Exception if an error occurs
     */
    protected abstract void setLastEventSequenceNumber(String consumerId, Long eventSequenceNumber) throws Exception;


    /**
     * Adds an event to the dead event queue for a specific consumer.
     *
     * @param consumerId              the ID of the consumer
     * @param publishedEvent     the PublishedEvent object representing the event to be added
     * @throws RuntimeException              if an error occurs during the addition of the event to the dead event queue
     */
    public abstract void addEventToDeadEventQueue(String consumerId, PublishedEvent publishedEvent, Exception exception) throws Exception;


    /**
     * Removes a published event from the dead event queue for a specific consumer.
     *
     * @param consumerId the ID of the consumer
     * @param publishedEvent the PublishedEvent object representing the event to be removed
     * @throws Exception if an error occurs during the removal of the event from the dead event queue
     */
    public void removeEventFromDeadEventQueue(String consumerId, PublishedEvent publishedEvent) throws Exception {
        removeEventFromDeadEventQueue(consumerId, publishedEvent.getEventSequenceNumber());
    }


    /**
     * Removes an event from the dead event queue for a specific consumer.
     *
     * @param consumerId             the ID of the consumer
     * @param eventSequenceNumber    the sequence number of the event to be removed
     * @throws Exception if an error occurs during the removal of the event from the dead event queue
     */
    public abstract void removeEventFromDeadEventQueue(String consumerId, long eventSequenceNumber) throws Exception;


    /**
     * Retrieves the events to be reprocessed from the dead event queue for a specific consumer and context.
     *
     * @param consumerId the ID of the consumer
     * @return an Iterable of PublishedEvent objects representing the events to be reprocessed
     * @throws Exception if an error occurs during retrieval of events from the dead event queue
     */
    protected abstract Collection<PublishedEvent> getEventsToReprocessFromDeadEventQueue(String consumerId) throws Exception;



    /**
     * Retrieves the events from the dead event queue for a specific consumer.
     *
     * @param consumerId the ID of the consumer
     * @return a Collection of PublishedEvent objects representing the events from the dead event queue
     * @throws Exception if an error occurs during retrieval of events from the dead event queue
     */
    public abstract Collection<DeadPublishedEvent> getEventsFromDeadEventQueue(String consumerId) throws Exception;

    /**
     * Sets the retry flag for a dead event of a specific consumer.
     *
     * @param consumerId          the ID of the consumer
     * @param eventSequenceNumber the sequence number of the dead event
     * @param retry               the retry flag, true if the event should be retried, false otherwise
     * @throws Exception if an error occurs during the retry flag setting
     */
    public abstract void setRetryDeadEvent(String consumerId, long eventSequenceNumber, boolean retry) throws Exception;

    /**
     * Retrieves the stored state of a saga identified by its name and association property and value.
     *
     * @param sagaName            the name of the saga
     * @param associationProperty the property used for association
     * @param associationValue    the value used for association
     * @return the stored saga state
     * @throws Exception if an error occurs during retrieval of saga state
     */
    public abstract StoredSagaState getSagaState(String sagaName, String associationProperty, String associationValue) throws Exception;

    /**
     * Retrieves the stored states of sagas with the specified name.
     *
     * @param sagaName the name of the saga
     * @return a collection of StoredSagaState objects representing the stored saga states
     * @throws Exception if an error occurs during retrieval of the saga states
     */
    public abstract Collection<StoredSagaState> getSagaStates(String sagaName) throws Exception;

    /**
     * Sets the state of a saga identified by its ID, name, and SagaState object.
     *
     * @param sagaId    the ID of the saga
     * @param sagaName  the name of the saga
     * @param sagaState the SagaState object representing the state of the saga
     * @throws Exception if an error occurs during setting the saga state
     */
    public abstract void setSagaState(Long sagaId, String sagaName, SagaState sagaState) throws Exception;

    /**
     * Retrieves the instance of ObjectMapper used for JSON serialization and deserialization.
     *
     * @return the ObjectMapper instance
     */
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
