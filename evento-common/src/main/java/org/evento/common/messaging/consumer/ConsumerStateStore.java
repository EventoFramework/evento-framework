package org.evento.common.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.modeling.messaging.dto.PublishedEvent;
import org.evento.common.modeling.state.SagaState;
import org.evento.common.performance.PerformanceService;
import org.evento.common.serialization.ObjectMapperUtils;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ConsumerStateStore {

	protected final MessageBus messageBus;
	protected final String serverNodeName;
	private final Logger logger = LogManager.getLogger(ConsumerStateStore.class);
	private final PerformanceService performanceService;
	private final String bundleId;
	private final ObjectMapper objectMapper;

	protected ConsumerStateStore(
			MessageBus messageBus,
			String bundleId,
			String serverNodeName,
			PerformanceService performanceService) {
		this.messageBus = messageBus;
		this.serverNodeName = serverNodeName;
		this.bundleId = bundleId;
		this.performanceService = performanceService;
		this.objectMapper = ObjectMapperUtils.getPayloadObjectMapper();
	}

	public int consumeEventsForProjector(
			String consumerId,
			String projectorName, ProjectorEventConsumer projectorEventConsumer,
			int fetchSize) throws Throwable {
		var consumedEventCount = 0;
		if (enterExclusiveZone(consumerId))
		{
			try
			{
				var lastEventSequenceNumber = getLastEventSequenceNumber(consumerId);
				if (lastEventSequenceNumber == null) lastEventSequenceNumber = 0L;
				var resp = ((EventFetchResponse) messageBus.request(messageBus.getNodeAddress(serverNodeName),
						new EventFetchRequest(
								lastEventSequenceNumber,
								fetchSize,
								projectorName)).get());
				for (PublishedEvent event : resp.getEvents())
				{
					var start = Instant.now();
					projectorEventConsumer.consume(event);
					setLastEventSequenceNumber(consumerId, event.getEventSequenceNumber());
					consumedEventCount++;
					performanceService.sendServiceTimeMetric(
							bundleId,
							projectorName,
							event.getEventMessage(),
							start
					);
				}
			} finally
			{
				leaveExclusiveZone(consumerId);
			}
		} else
		{
			return -1;
		}
		return consumedEventCount;

	}

	public int consumeEventsForSaga(String consumerId, String sagaName, SagaEventConsumer sagaEventConsumer,
									int fetchSize) throws Throwable {
		var consumedEventCount = 0;
		if (enterExclusiveZone(consumerId))
		{
			try
			{
				var lastEventSequenceNumber = getLastEventSequenceNumberSagaOrHead(consumerId);
				var resp = ((EventFetchResponse) messageBus.request(messageBus.findNodeAddress(serverNodeName),
						new EventFetchRequest(lastEventSequenceNumber, fetchSize, sagaName)).get());
				for (PublishedEvent event : resp.getEvents())
				{
					var start = Instant.now();
					var sagaStateId = new AtomicReference<Long>();
					var newState = sagaEventConsumer.consume((name, associationProperty, associationValue) -> {
						var state = getSagaState(name, associationProperty, associationValue);
						sagaStateId.set(state.getId());
						return state.getState();
					}, event);
					if (newState != null)
					{
						if (newState.isEnded())
						{
							removeSagaState(sagaStateId.get());
						} else
						{
							setSagaState(sagaStateId.get(), sagaName, newState);
						}
					}
					setLastEventSequenceNumber(consumerId, event.getEventSequenceNumber());
					consumedEventCount++;
					performanceService.sendServiceTimeMetric(
							bundleId,
							sagaName,
							event.getEventMessage(),
							start
					);
				}
			} finally
			{
				leaveExclusiveZone(consumerId);
			}
		} else
		{
			return -1;
		}
		return consumedEventCount;
	}


	protected long getLastEventSequenceNumberSagaOrHead(String consumerId) throws Exception {
		var last = getLastEventSequenceNumber(consumerId);
		if (last == null)
		{
			var head = ((EventLastSequenceNumberResponse) this.messageBus.request(messageBus.getNodeAddress(serverNodeName), new EventLastSequenceNumberRequest()).get()).getNumber();
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
