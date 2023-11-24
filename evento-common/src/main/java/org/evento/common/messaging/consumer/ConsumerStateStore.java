package org.evento.common.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.messaging.bus.EventoServer;
import org.evento.common.modeling.messaging.dto.PublishedEvent;
import org.evento.common.modeling.state.SagaState;
import org.evento.common.performance.PerformanceService;
import org.evento.common.serialization.ObjectMapperUtils;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ConsumerStateStore {

    private final Logger logger = LogManager.getLogger(ConsumerStateStore.class);
    protected final EventoServer eventoServer;
    private final PerformanceService performanceService;
    private final ObjectMapper objectMapper;

    protected ConsumerStateStore(
            EventoServer eventoServer,
            PerformanceService performanceService) {
        this.eventoServer = eventoServer;
        this.performanceService = performanceService;
        this.objectMapper = ObjectMapperUtils.getPayloadObjectMapper();
    }

    private static Object monitor = new Object();

    public int consumeEventsForProjector(
            String consumerId,
            String projectorName,
            String context,
            ProjectorEventConsumer projectorEventConsumer,
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
                        throw new RuntimeException("Event consumption Error for projection %s and event %s".formatted(projectorName, event.getEventName()), e);
                    }
                    setLastEventSequenceNumber(consumerId, event.getEventSequenceNumber());
                    consumedEventCount++;
                    performanceService.sendServiceTimeMetric(
                            eventoServer.getBundleId(),
                            projectorName,
                            event.getEventMessage(),
                            start
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
                        throw new RuntimeException("Event consumption Error for saga %s and event %s".formatted(sagaName, event.getEventName()), e);
                    }

                    setLastEventSequenceNumber(consumerId, event.getEventSequenceNumber());
                    consumedEventCount++;
                    performanceService.sendServiceTimeMetric(
                            eventoServer.getBundleId(),
                            sagaName,
                            event.getEventMessage(),
                            start
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


    protected long getLastEventSequenceNumberSagaOrHead(String consumerId) throws Exception {
        var last = getLastEventSequenceNumber(consumerId);
        if (last == null) {
            var head = ((EventLastSequenceNumberResponse) this.eventoServer.request(new EventLastSequenceNumberRequest()).get()).getNumber();
            setLastEventSequenceNumber(consumerId, head);
            return head;
        }
        return last;
    }

    protected abstract void removeSagaState(Long sagaId) throws Exception;

    protected abstract void leaveExclusiveZone(String consumerId) throws Exception;

    protected abstract boolean enterExclusiveZone(String consumerId) throws Exception;

    protected abstract Long getLastEventSequenceNumber(String consumerId) throws Exception;

    protected abstract void setLastEventSequenceNumber(String consumerId, Long eventSequenceNumber) throws Exception;

    protected abstract StoredSagaState getSagaState(String sagaName, String associationProperty, String associationValue) throws Exception;

    protected abstract void setSagaState(Long sagaId, String sagaName, SagaState sagaState) throws Exception;

    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
