package org.eventrails.common.messaging.consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.modeling.annotations.component.Saga;
import org.eventrails.common.modeling.messaging.dto.PublishedEvent;
import org.eventrails.common.modeling.state.SagaState;
import org.eventrails.common.performance.PerformanceService;

import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

public abstract class ConsumerStateStore {

    private final Logger logger = LogManager.getLogger(ConsumerStateStore.class);


    private final MessageBus messageBus;
    private final String serverNodeName;

    private final PerformanceService performanceService;
    private final String bundleId;

    protected ConsumerStateStore(MessageBus messageBus, String bundleId, String serverNodeName) {
        this.messageBus = messageBus;
        this.serverNodeName = serverNodeName;
        this.bundleId = bundleId;
        this.performanceService = new PerformanceService(messageBus, serverNodeName);
    }

    public int consumeEventsForProjector(
            String consumerId,
            String projectorName, ProjectorEventConsumer projectorEventConsumer,
                                         int fetchSize) throws Throwable {
        var consumedEventCount = 0;
        if (enterExclusiveZone(consumerId)) {
            try {
                var lastEventSequenceNumber = getLastEventSequenceNumber(consumerId);
                var resp = ((EventFetchResponse) messageBus.request(messageBus.findNodeAddress(serverNodeName),
                        new EventFetchRequest(lastEventSequenceNumber, fetchSize)).get());
                for (PublishedEvent event : resp.getEvents()) {
                    performanceService.sendPerformances(
                            PerformanceService.DISPATCHER,
                            projectorName,
                            event.getEventName(),
                            Instant.ofEpochMilli(event.getCreatedAt())
                    );
                    var start = Instant.now();
                    projectorEventConsumer.consume(event);
                    setLastEventSequenceNumber(consumerId, event.getEventSequenceNumber());
                    consumedEventCount++;
                    performanceService.sendPerformances(
                            bundleId,
                            projectorName,
                            event.getEventName(),
                            start
                    );
                }
            } finally {
                leaveExclusiveZone(consumerId);
            }
        }
        return consumedEventCount;

    }

    public int consumeEventsForSaga(String consumerId, String sagaName, SagaEventConsumer sagaEventConsumer,
                                    int fetchSize) throws Throwable {
        var consumedEventCount = 0;
        if (enterExclusiveZone(consumerId)) {
            try {
                var lastEventSequenceNumber = getLastEventSequenceNumber(consumerId);
                var resp = ((EventFetchResponse) messageBus.request(messageBus.findNodeAddress(serverNodeName),
                        new EventFetchRequest(lastEventSequenceNumber, fetchSize)).get());
                for (PublishedEvent event : resp.getEvents()){
                    performanceService.sendPerformances(
                            PerformanceService.DISPATCHER,
                            sagaName,
                            event.getEventName(),
                            Instant.ofEpochMilli(event.getCreatedAt())
                    );
                    var start = Instant.now();
                    var sagaStateId = new AtomicReference<Long>();
                    var newState = sagaEventConsumer.consume((name, associationProperty, associationValue) -> {
                        var state = getSagaState(name, associationProperty, associationValue);
                        sagaStateId.set(state.getId());
                        return state.getState();
                    }, event);
                    if(newState != null) {
                        if(newState.isEnded()){
                            removeSagaState(sagaStateId.get());
                        }else {
                            setSagaState(sagaStateId.get(), sagaName, newState);
                        }
                    }
                    setLastEventSequenceNumber(consumerId, event.getEventSequenceNumber());
                    consumedEventCount++;
                    performanceService.sendPerformances(
                            bundleId,
                            sagaName,
                            event.getEventName(),
                            start
                    );
                }
            } finally {
                leaveExclusiveZone(consumerId);
            }
        }
        return consumedEventCount;
    }

    protected abstract void removeSagaState(Long sagaId) throws Exception;

    protected abstract void leaveExclusiveZone(String consumerId) throws Exception;

    protected abstract boolean enterExclusiveZone(String consumerId) throws Exception;

    protected abstract Long getLastEventSequenceNumber(String consumerId) throws Exception;
    protected abstract void setLastEventSequenceNumber(String consumerId, Long eventSequenceNumber) throws Exception;
    protected abstract StoredSagaState getSagaState(String sagaName, String associationProperty, String associationValue) throws Exception;
    protected abstract void setSagaState(Long sagaId, String sagaName, SagaState sagaState) throws Exception;
}
