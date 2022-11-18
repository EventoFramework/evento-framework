package org.eventrails.server.service.messaging;

import org.eventrails.common.modeling.messaging.dto.PublishedEvent;
import org.eventrails.common.modeling.messaging.message.application.EventToProjectorMessage;
import org.eventrails.common.modeling.messaging.message.application.EventToSagaMessage;
import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.common.messaging.utils.AddressPicker;
import org.eventrails.common.messaging.utils.RoundRobinAddressPicker;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

@Service
public class EventDispatcher {

	private final BundleRepository bundleRepository;

	private final SagaStateRepository sagaStateRepository;

	private final ComponentEventConsumingStateRepository componentEventConsumingStateRepository;

	private final MessageBus messageBus;

	private final HandlerService handlerService;

	private final EventStore eventStore;
	private final String serverNodeName;

	private final AddressPicker addressPicker;

	private final BundleDeployService bundleDeployService;

	private final PerformanceService performanceService;


	public EventDispatcher(
			BundleRepository bundleRepository,
			SagaStateRepository sagaStateRepository,
			ComponentEventConsumingStateRepository componentEventConsumingStateRepository,
			MessageBus messageBus,
			HandlerService handlerService,
			EventStore eventStore,
			@Value("${eventrails.cluster.node.server.name}") String serverNodeName, BundleDeployService bundleDeployService, PerformanceService performanceService) {
		this.bundleRepository = bundleRepository;
		this.sagaStateRepository = sagaStateRepository;
		this.componentEventConsumingStateRepository = componentEventConsumingStateRepository;
		this.messageBus = messageBus;
		this.handlerService = handlerService;
		this.eventStore = eventStore;
		this.serverNodeName = serverNodeName;
		this.addressPicker = new RoundRobinAddressPicker(messageBus);
		this.bundleDeployService = bundleDeployService;
		this.performanceService = performanceService;
		eventConsumer();
	}

	private void eventConsumer() {

		var dispatcherAddresses = fetchDispatcherAddresses().stream().map(NodeAddress::getAddress).toList();
		var nodeIndex = dispatcherAddresses.indexOf(messageBus.getAddress().getAddress());
		var nodeCount = dispatcherAddresses.size();

		var bundles = bundleRepository.findAll();
		var managedBundles = new ArrayList<Bundle>();

		for (int i = 0; i < bundles.size(); i++)
		{
			if (i % nodeCount == nodeIndex)
			{
				managedBundles.add(bundles.get(i));
			}
		}

		for (Bundle bundle : managedBundles)
		{
			for (Map.Entry<String, List<Handler>> entry : handlerService.findAllEventHandlersByBundle(bundle)
					.stream().collect(groupingBy(Handler::getComponentName)).entrySet())
			{

				if (entry.getValue().get(0).getHandlerType() == HandlerType.EventHandler)
				{
					new Thread(() -> processProjectorEvents(
							bundle.getName(),
							entry.getKey(),
							new ArrayList<>(),
							entry.getValue(),
							eventStore,
							componentEventConsumingStateRepository), entry.getKey() + " - EventConsumerThread").start();
				} else if (entry.getValue().get(0).getHandlerType() == HandlerType.SagaEventHandler)
				{
					new Thread(() -> processSagaEvents(
							bundle.getName(),
							entry.getKey(),
							new ArrayList<>(),
							entry.getValue(),
							eventStore,
							componentEventConsumingStateRepository,
							sagaStateRepository), entry.getKey() + " - EventConsumerThread").start();
				}
			}
		}
	}

	private interface CodeBlock {
		void run() throws Exception;
	}

	private void retryBlock(CodeBlock runnable) {
		while (true)
		{
			//var t = transactionManager.getTransaction(null);
			try
			{
				runnable.run();
				//transactionManager.commit(t);
				return;
			} catch (Exception e)
			{
				e.printStackTrace();
				try
				{
					Thread.sleep(3 * 1000);
				} catch (InterruptedException ignored)
				{}
				//transactionManager.rollback(t);
			}
		}
	}


	private void processSagaEvents(
			String bundleName,
			String sagaName,
			List<PublishedEvent> events,
			List<Handler> handlers,
			EventStore eventStore,
			ComponentEventConsumingStateRepository repository,
			SagaStateRepository sagaStateRepository) {
		retryBlock(() -> {
			while (events.isEmpty())
			{
				Thread.sleep(1000);
				var state = repository
						.findById(sagaName)
						.orElseGet(() ->
							repository.save(new EventConsumerState(
									sagaName,
									bundleName,
									eventStore.getLastEventSequenceNumber()))
						);
				events.addAll(eventStore.fetchEvents(state.getLastEventSequenceNumber()).stream().map(EventStoreEntry::toPublishedEvent).toList());
			}
			var event = events.get(0);

			var handler = handlers.stream()
					.filter(h -> h.getHandledPayload().getName().equals(event.getEventName())).findFirst();
			if (handler.isEmpty())
			{
				retryBlock(() -> {
					repository.save(new EventConsumerState(
							sagaName,
							bundleName,
							event.getEventSequenceNumber()));

					processSagaEvents(
							bundleName, sagaName,
							events.size() == 1 ? new ArrayList<PublishedEvent>() : events.subList(1, events.size()),
							handlers, eventStore,
							repository, sagaStateRepository);
				});
			} else
			{
				var startTime = Instant.now();
				var associationProperty = handler.get().getAssociationProperty();
				var associationValue = event.getEventMessage().getAssociationValue(associationProperty);

				var sagaState = sagaStateRepository.getLastStatus(
								sagaName,
								associationProperty,
								associationValue)
						.orElse(new SagaState(sagaName, new SerializedSagaState<>(null)));

				bundleDeployService.waitUntilAvailable(bundleName);
				var dest = addressPicker.pickNodeAddress(bundleName);
				messageBus.request(dest,
						new EventToSagaMessage(event.getEventMessage(),
								sagaState.getSerializedSagaState(),
								sagaName
						)
						,
						resp -> {
							performanceService.updatePerformances(
									dest.getNodeName(),
									handlers.stream().map(Handler::getComponentName).distinct().collect(Collectors.toList()),
									event.getEventName(),
									startTime
							);
							retryBlock(() -> {
								if (!((SerializedSagaState<?>) resp).isEnded())
									sagaStateRepository.save(new SagaState(
											sagaState.getId(),
											sagaState.getSagaName(),
											(SerializedSagaState<?>) resp
									));
								else
									sagaStateRepository.delete(sagaState);
								repository.save(new EventConsumerState(
										sagaName,
										bundleName,
										event.getEventSequenceNumber()));

								processSagaEvents(
										bundleName, sagaName,
										events.size() == 1 ? new ArrayList<>() : events.subList(1, events.size()), handlers, eventStore,
										repository, sagaStateRepository);
							});
						},
						error -> {
							performanceService.updatePerformances(
									dest.getNodeName(),
									handlers.stream().map(Handler::getComponentName).distinct().collect(Collectors.toList()),
									event.getEventName(),
									startTime
							);
							error.toException().printStackTrace();
							processSagaEvents(
									bundleName, sagaName, events,
									handlers, eventStore,
									repository, sagaStateRepository);

						});
			}
		});

	}

	private void processProjectorEvents(
			String bundleName,
			String projectorName,
			List<PublishedEvent> events,
			List<Handler> handlers,
			EventStore eventStore,
			ComponentEventConsumingStateRepository repository) {
		retryBlock(() -> {
			while (events.isEmpty())
			{
				Thread.sleep(1000);
				var state = componentEventConsumingStateRepository
						.findById(projectorName)
						.orElse(new EventConsumerState(
								projectorName,
								bundleName,
								0L));
				events.addAll(eventStore.fetchEvents(state.getLastEventSequenceNumber()).stream().map(EventStoreEntry::toPublishedEvent).toList());
			}
			var event = events.get(0);
			if (handlers.stream()
					.noneMatch(h -> h.getHandledPayload().getName().equals(event.getEventName())))
			{
				processNextProjectorEvent(bundleName, projectorName, events, handlers, eventStore, repository, event);
			} else
			{

				var startTime = Instant.now();
				bundleDeployService.waitUntilAvailable(bundleName);
				var dest = addressPicker.pickNodeAddress(bundleName);
				messageBus.request(dest,
						new EventToProjectorMessage(event.getEventMessage(), projectorName),
						resp -> {
							performanceService.updatePerformances(
									dest.getNodeName(),
									handlers.stream().map(Handler::getComponentName).distinct().collect(Collectors.toList()),
									event.getEventName(),
									startTime
							);
							processNextProjectorEvent(bundleName, projectorName, events, handlers, eventStore, repository, event);
						},
						err -> {
							performanceService.updatePerformances(
									dest.getNodeName(),
									handlers.stream().map(Handler::getComponentName).distinct().collect(Collectors.toList()),
									event.getEventName(),
									startTime
							);
							err.toException().printStackTrace();
							processProjectorEvents(bundleName, projectorName, events, handlers, eventStore, repository);
						});
			}


		});
	}

	private void processNextProjectorEvent(
			String bundleName,
			String projectorName,
			List<PublishedEvent> events, List<Handler> handlers, EventStore eventStore,
			ComponentEventConsumingStateRepository repository, PublishedEvent event) {
		retryBlock(() -> {
			repository.save(new EventConsumerState(
					projectorName,
					bundleName,
					event.getEventSequenceNumber()));

			processProjectorEvents(
					bundleName,
					projectorName,
					events.size() == 1 ? new ArrayList<PublishedEvent>() : events.subList(1, events.size()),
					handlers,
					eventStore,
					repository);
		});
	}


	private Set<NodeAddress> fetchDispatcherAddresses() {
		return this.messageBus.findAllNodeAddresses(serverNodeName);
	}


}
