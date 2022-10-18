package org.eventrails.server.service.messaging;

import org.eventrails.modeling.gateway.PublishedEvent;
import org.eventrails.modeling.messaging.message.EventToProjectorMessage;
import org.eventrails.modeling.messaging.message.EventToSagaMessage;
import org.eventrails.modeling.messaging.message.bus.MessageBus;
import org.eventrails.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.modeling.messaging.utils.RoundRobinAddressPicker;
import org.eventrails.modeling.state.SerializedSagaState;
import org.eventrails.server.domain.model.ComponentEventConsumingState;
import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.model.Ranch;
import org.eventrails.server.domain.model.SagaState;
import org.eventrails.server.domain.model.types.HandlerType;
import org.eventrails.server.domain.repository.ComponentEventConsumingStateRepository;
import org.eventrails.server.domain.repository.RanchRepository;
import org.eventrails.server.domain.repository.SagaStateRepository;
import org.eventrails.server.es.EventStore;
import org.eventrails.server.es.eventstore.EventStoreEntry;
import org.eventrails.server.service.HandlerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;

@Service
public class EventDispatcher {

	private final RanchRepository ranchRepository;

	private final SagaStateRepository sagaStateRepository;

	private final ComponentEventConsumingStateRepository componentEventConsumingStateRepository;

	private final MessageBus messageBus;

	private final HandlerService handlerService;

	private final EventStore eventStore;
	private final String serverNodeName;

	private final RoundRobinAddressPicker roundRobinAddressPicker;

	public EventDispatcher(
			RanchRepository ranchRepository,
			SagaStateRepository sagaStateRepository,
			ComponentEventConsumingStateRepository componentEventConsumingStateRepository,
			MessageBus messageBus,
			HandlerService handlerService,
			EventStore eventStore,
			@Value("${eventrails.cluster.node.server.name}") String serverNodeName) {
		this.ranchRepository = ranchRepository;
		this.sagaStateRepository = sagaStateRepository;
		this.componentEventConsumingStateRepository = componentEventConsumingStateRepository;
		this.messageBus = messageBus;
		this.handlerService = handlerService;
		this.eventStore = eventStore;
		this.serverNodeName = serverNodeName;
		this.roundRobinAddressPicker = new RoundRobinAddressPicker(messageBus);
		//eventConsumer();
	}

	private void eventConsumer() {

		var dispatcherAddresses = fetchDispatcherAddresses().stream().map(NodeAddress::getAddress).toList();
		var nodeIndex = dispatcherAddresses.indexOf(messageBus.getAddress().getAddress());
		var nodeCount = dispatcherAddresses.size();

		var ranches = ranchRepository.findAll();
		var managedRanches = new ArrayList<Ranch>();

		for (int i = 0; i < ranches.size(); i++)
		{
			if (i % nodeCount == nodeIndex)
			{
				managedRanches.add(ranches.get(i));
			}
		}

		for (Ranch ranch : managedRanches)
		{
			for (Map.Entry<String, List<Handler>> entry : handlerService.findAllEventHandlersByRanch(ranch)
					.stream().collect(groupingBy(Handler::getComponentName)).entrySet())
			{

				if (entry.getValue().get(0).getHandlerType() == HandlerType.EventHandler)
				{
					new Thread(() -> processProjectorEvents(
							ranch.getName(),
							entry.getKey(),
							new ArrayList<>(),
							entry.getValue(),
							eventStore,
							componentEventConsumingStateRepository), entry.getKey() + " - EventConsumerThread").start();
				} else if (entry.getValue().get(0).getHandlerType() == HandlerType.SagaEventHandler)
				{
					new Thread(() -> processSagaEvents(
							ranch.getName(),
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
			String ranchName,
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
							repository.save(new ComponentEventConsumingState(
									sagaName,
									ranchName,
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
					repository.save(new ComponentEventConsumingState(
							sagaName,
							ranchName,
							event.getEventSequenceNumber()));

					processSagaEvents(
							ranchName, sagaName,
							events.size() == 1 ? new ArrayList<PublishedEvent>() : events.subList(1, events.size()),
							handlers, eventStore,
							repository, sagaStateRepository);
				});
			} else
			{
				var associationProperty = handler.get().getAssociationProperty();
				var associationValue = event.getEventMessage().getAssociationValue(associationProperty);

				var sagaState = sagaStateRepository.getLastStatus(
								sagaName,
								associationProperty,
								associationValue)
						.orElse(new SagaState(sagaName, new SerializedSagaState<>(null)));

				messageBus.cast(roundRobinAddressPicker.pickNodeAddress(ranchName),
						new EventToSagaMessage(event.getEventMessage(),
								sagaState.getSerializedSagaState(),
								sagaName
						)
						,
						resp -> {
							retryBlock(() -> {
								if (!((SerializedSagaState<?>) resp).isEnded())
									sagaStateRepository.save(new SagaState(
											sagaState.getId(),
											sagaState.getSagaName(),
											(SerializedSagaState<?>) resp
									));
								else
									sagaStateRepository.delete(sagaState);
								repository.save(new ComponentEventConsumingState(
										sagaName,
										ranchName,
										event.getEventSequenceNumber()));

								processSagaEvents(
										ranchName, sagaName,
										events.size() == 1 ? new ArrayList<>() : events.subList(1, events.size()), handlers, eventStore,
										repository, sagaStateRepository);
							});

						},
						error -> {
							error.toException().printStackTrace();
							processSagaEvents(
									ranchName, sagaName, events,
									handlers, eventStore,
									repository, sagaStateRepository);

						});
			}
		});

	}

	private void processProjectorEvents(
			String ranchName,
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
						.orElse(new ComponentEventConsumingState(
								projectorName,
								ranchName,
								0L));
				events.addAll(eventStore.fetchEvents(state.getLastEventSequenceNumber()).stream().map(EventStoreEntry::toPublishedEvent).toList());
			}
			var event = events.get(0);
			if (handlers.stream()
					.noneMatch(h -> h.getHandledPayload().getName().equals(event.getEventName())))
			{
				processNextProjectorEvent(ranchName, projectorName, events, handlers, eventStore, repository, event);
			} else
			{
				messageBus.cast(roundRobinAddressPicker.pickNodeAddress(ranchName),
						new EventToProjectorMessage(event.getEventMessage(), projectorName),
						resp -> {
							processNextProjectorEvent(ranchName, projectorName, events, handlers, eventStore, repository, event);
						},
						err -> {
							err.toException().printStackTrace();
							processProjectorEvents(ranchName, projectorName, events, handlers, eventStore, repository);
						});
			}


		});
	}

	private void processNextProjectorEvent(
			String ranchName,
			String projectorName,
			List<PublishedEvent> events, List<Handler> handlers, EventStore eventStore,
			ComponentEventConsumingStateRepository repository, PublishedEvent event) {
		retryBlock(() -> {
			repository.save(new ComponentEventConsumingState(
					projectorName,
					ranchName,
					event.getEventSequenceNumber()));

			processProjectorEvents(
					ranchName,
					projectorName,
					events.size() == 1 ? new ArrayList<PublishedEvent>() : events.subList(1, events.size()),
					handlers,
					eventStore,
					repository);
		});
	}


	private List<NodeAddress> fetchDispatcherAddresses() {
		return this.messageBus.findAllNodeAddresses(serverNodeName);
	}


}
