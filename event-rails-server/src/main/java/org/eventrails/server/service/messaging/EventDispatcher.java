package org.eventrails.server.service.messaging;

import org.eventrails.common.modeling.bundle.types.ComponentType;
import org.eventrails.common.modeling.messaging.dto.PublishedEvent;
import org.eventrails.common.modeling.messaging.message.application.EventToProjectorMessage;
import org.eventrails.common.modeling.messaging.message.application.EventToSagaMessage;
import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.common.messaging.utils.AddressPicker;
import org.eventrails.common.messaging.utils.RoundRobinAddressPicker;
import org.eventrails.common.modeling.messaging.message.bus.ResponseSender;
import org.eventrails.common.modeling.messaging.message.internal.processor.FetchEventLockAlreadyAcquiredException;
import org.eventrails.common.modeling.messaging.message.internal.processor.FetchEventRequest;
import org.eventrails.common.modeling.messaging.message.internal.processor.FetchEventResponse;
import org.eventrails.common.modeling.state.SerializedSagaState;
import org.eventrails.server.domain.model.EventConsumerState;
import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.model.Bundle;
import org.eventrails.server.domain.model.SagaState;
import org.eventrails.common.modeling.bundle.types.HandlerType;
import org.eventrails.server.domain.repository.ComponentEventConsumingStateRepository;
import org.eventrails.server.domain.repository.BundleRepository;
import org.eventrails.server.domain.repository.SagaStateRepository;
import org.eventrails.server.es.EventStore;
import org.eventrails.server.es.eventstore.EventStoreEntry;
import org.eventrails.server.service.HandlerService;
import org.eventrails.server.service.deploy.BundleDeployService;
import org.eventrails.server.service.performance.PerformanceService;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

@Service
public class EventDispatcher {

	private static final String CONSUMER_LOCK_PREFIX = "CONSUMER:";
	private final BundleRepository bundleRepository;

	private final SagaStateRepository sagaStateRepository;

	private final ComponentEventConsumingStateRepository componentEventConsumingStateRepository;

	private final MessageBus messageBus;

	private final HandlerService handlerService;

	private final EventStore eventStore;
	private final String serverNodeName;

	private final PerformanceService performanceService;
	private final LockRegistry lockRegistry;

	private boolean isShuttingDown = false;


	public EventDispatcher(
			BundleRepository bundleRepository,
			SagaStateRepository sagaStateRepository,
			ComponentEventConsumingStateRepository componentEventConsumingStateRepository,
			MessageBus messageBus,
			HandlerService handlerService,
			EventStore eventStore,
			@Value("${eventrails.cluster.node.server.id}") String serverNodeName,
			PerformanceService performanceService,
			LockRegistry lockRegistry) {
		this.bundleRepository = bundleRepository;
		this.sagaStateRepository = sagaStateRepository;
		this.componentEventConsumingStateRepository = componentEventConsumingStateRepository;
		this.messageBus = messageBus;
		this.handlerService = handlerService;
		this.eventStore = eventStore;
		this.serverNodeName = serverNodeName;
		this.performanceService = performanceService;
		this.lockRegistry = lockRegistry;
		Runtime.getRuntime().addShutdownHook(new Thread(() -> isShuttingDown = true));
	}

	public void handleRequest(NodeAddress source, FetchEventRequest d, ResponseSender response) {
		var consumerId = source.getBundleId() + "_" + source.getBundleVersion() + "_" + d.getComponentName();
		var lock = lockRegistry.obtain(CONSUMER_LOCK_PREFIX + consumerId);
		if (lock.tryLock())
		{
			try
			{
				var state = componentEventConsumingStateRepository
						.findById(consumerId)
						.orElseGet(() ->
								componentEventConsumingStateRepository.save(new EventConsumerState(
										consumerId,
										d.getComponentType() == ComponentType.Saga ?
												eventStore.getLastEventSequenceNumber() : 0))
						);
				processEvents(
						eventStore.fetchEvents(state.getLastEventSequenceNumber()).stream().map(EventStoreEntry::toPublishedEvent).toList(),
						consumerId,
						d.getComponentType(),
						d.getComponentName(),
						source,
						response,
						lock,
						0
				);
			} catch (Exception e)
			{
				response.sendError(e);
				lock.unlock();
			}

		} else
		{
			response.sendResponse(new FetchEventResponse(false, 0));
		}
	}

	private void processEvents(
			List<PublishedEvent> events,
			String consumerId,
			ComponentType componentType,
			String componentName,
			NodeAddress source,
			ResponseSender response,
			Lock lock,
			long count) {
		if (events.isEmpty() || isShuttingDown)
		{
			response.sendResponse(new FetchEventResponse(true, count));
			lock.unlock();
			return;
		}
		var event = events.get(0);

		performanceService.updatePerformances(
				PerformanceService.DISPATCHER,
				componentName,
				event.getEventName(),
				Instant.ofEpochMilli(event.getCreatedAt())
		);

		try
		{
			var oHandler = handlerService.findById(
					Handler.generateId(
							source.getBundleId(),
							componentName,
							componentType,
							event.getEventName()
					)
			);

			if (oHandler.isPresent())
			{
				var startTime = Instant.now();

				if (componentType == ComponentType.Projector)
				{
					messageBus.request(source,
							new EventToProjectorMessage(event.getEventMessage(), componentName),
							resp -> {
								try
								{
									componentEventConsumingStateRepository.save(new EventConsumerState(
											consumerId,
											event.getEventSequenceNumber()));
									performanceService.updatePerformances(
											source.getBundleId(),
											componentName,
											event.getEventName(),
											startTime
									);
									processEvents(events.size() == 1 ? new ArrayList<PublishedEvent>() : events.subList(1, events.size()),
											consumerId,
											componentType,
											componentName,
											source,
											response,
											lock,
											count + 1);
								} catch (Exception e)
								{
									response.sendError(e);
									lock.unlock();
								}
							},
							err -> {
								response.sendError(err.toThrowable());
								lock.unlock();
							});
				} else if (componentType == ComponentType.Saga)
				{
					var associationProperty = oHandler.get().getAssociationProperty();
					var associationValue = event.getEventMessage().getAssociationValue(associationProperty);

					var sagaState = sagaStateRepository.getLastStatus(
									componentName,
									associationProperty,
									associationValue)
							.orElse(new SagaState(componentName, new SerializedSagaState<>(null)));
					messageBus.request(source,
							new EventToSagaMessage(event.getEventMessage(),
									sagaState.getSerializedSagaState(),
									componentName
							)
							,
							resp -> {
								try
								{
									if (!((SerializedSagaState<?>) resp).isEnded())
										sagaStateRepository.save(new SagaState(
												sagaState.getId(),
												sagaState.getSagaName(),
												(SerializedSagaState<?>) resp
										));
									else
										sagaStateRepository.delete(sagaState);
									componentEventConsumingStateRepository.save(new EventConsumerState(
											consumerId,
											event.getEventSequenceNumber()));

									performanceService.updatePerformances(
											source.getBundleId(),
											componentName,
											event.getEventName(),
											startTime
									);

									processEvents(events.size() == 1 ? new ArrayList<PublishedEvent>() : events.subList(1, events.size()),
											consumerId,
											componentType,
											componentName,
											source,
											response,
											lock,
											count + 1);
								} catch (Exception e)
								{
									response.sendError(e);
									lock.unlock();
								}
							},
							error -> {
								response.sendError(error.toThrowable());
								lock.unlock();
							});
				}
			} else
			{
				componentEventConsumingStateRepository.save(new EventConsumerState(
						consumerId,
						event.getEventSequenceNumber()));
				processEvents(events.size() == 1 ? new ArrayList<PublishedEvent>() : events.subList(1, events.size()),
						consumerId,
						componentType,
						componentName,
						source,
						response,
						lock,
						count + 1);
			}


		} catch (Exception e)
		{
			response.sendError(e);
			lock.unlock();
		}
	}

	private Set<NodeAddress> fetchDispatcherAddresses() {
		return this.messageBus.findAllNodeAddresses(serverNodeName);
	}


}
