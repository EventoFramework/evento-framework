package org.evento.common.messaging.consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.modeling.messaging.dto.PublishedEvent;
import org.evento.common.modeling.messaging.message.application.EventMessage;
import org.evento.common.modeling.state.SagaState;
import org.evento.common.performance.PerformanceService;
import org.evento.common.messaging.bus.MessageBus;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ConsumerStateStore {

    private final Logger logger = LogManager.getLogger(ConsumerStateStore.class);


    protected final MessageBus messageBus;
    protected final String serverNodeName;

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
                if(lastEventSequenceNumber == null) lastEventSequenceNumber = 0L;
                var resp = ((EventFetchResponse) messageBus.request(messageBus.getNodeAddress(serverNodeName),
                        new EventFetchRequest(
                                lastEventSequenceNumber,
                                fetchSize,
                                projectorName)).get());
                for (PublishedEvent event : resp.getEvents()) {
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
        } else {
            return -1;
        }
        return consumedEventCount;

    }

    public int consumeEventsForSaga(String consumerId, String sagaName, SagaEventConsumer sagaEventConsumer,
                                    int fetchSize) throws Throwable {
        var consumedEventCount = 0;
        if (enterExclusiveZone(consumerId)) {
            try {
                var lastEventSequenceNumber = getLastEventSequenceNumberSagaOrHead(consumerId);
                var resp = ((EventFetchResponse) messageBus.request(messageBus.findNodeAddress(serverNodeName),
                        new EventFetchRequest(lastEventSequenceNumber, fetchSize, sagaName)).get());
                for (PublishedEvent event : resp.getEvents()) {
                    var start = Instant.now();
                    var sagaStateId = new AtomicReference<Long>();
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
        } else {
            return -1;
        }
        return consumedEventCount;
    }



    protected long getLastEventSequenceNumberSagaOrHead(String consumerId) throws Exception {
        var last = getLastEventSequenceNumber(consumerId);
        if (last == null) {
            var head =  ((EventLastSequenceNumberResponse) this.messageBus.request(messageBus.getNodeAddress(serverNodeName), new EventLastSequenceNumberRequest()).get()).getNumber();
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

}
