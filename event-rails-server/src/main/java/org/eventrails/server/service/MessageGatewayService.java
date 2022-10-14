package org.eventrails.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.eventrails.modeling.gateway.PublishedEvent;
import org.eventrails.modeling.messaging.message.bus.*;
import org.eventrails.server.domain.model.ComponentEventConsumingState;
import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.model.Ranch;
import org.eventrails.server.domain.model.SagaState;
import org.eventrails.server.domain.model.types.HandlerType;
import org.eventrails.server.domain.repository.RanchRepository;
import org.eventrails.server.domain.repository.ComponentEventConsumingStateRepository;
import org.eventrails.server.domain.repository.SagaStateRepository;
import org.eventrails.server.es.EventStore;
import org.eventrails.server.es.eventstore.EventStoreEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;

import static java.util.stream.Collectors.groupingBy;

@Service
public class MessageGatewayService {
	private final HandlerService handlerService;

	private final LockRegistry lockRegistry;

	private final ObjectMapper objectMapper;

	private final EventStore eventStore;

	private final MessageBus messageBus;
	private final Thread eventConsumer;
	private final LinkedBlockingDeque<PublishedEvent> eventQueue = new LinkedBlockingDeque<>();

	private final RanchRepository ranchRepository;

	private final SagaStateRepository sagaStateRepository;

	private final ComponentEventConsumingStateRepository componentEventConsumingStateRepository;

	private final PlatformTransactionManager transactionManager;


	private String serverNodeName;


	public MessageGatewayService(
			HandlerService handlerService,
			LockRegistry lockRegistry,
			ObjectMapper objectMapper,
			EventStore eventStore,
			MessageBus messageBus,
			RanchRepository ranchRepository,
			SagaStateRepository sagaStateRepository,
			ComponentEventConsumingStateRepository componentEventConsumingStateRepository, PlatformTransactionManager transactionManager,
			@Value("${eventrails.cluster.node.server.name}") String serverNodeName) {
		this.handlerService = handlerService;
		this.lockRegistry = lockRegistry;
		this.objectMapper = objectMapper;
		this.eventStore = eventStore;
		this.messageBus = messageBus;
		this.ranchRepository = ranchRepository;
		this.sagaStateRepository = sagaStateRepository;
		this.componentEventConsumingStateRepository = componentEventConsumingStateRepository;
		this.transactionManager = transactionManager;
		this.serverNodeName = serverNodeName;
		messageBus.setRequestReceiver(this::messageHandler);
		eventConsumer = new Thread(this::eventConsumer);
		eventConsumer.start();
	}

	private void messageHandler(Object request, ResponseSender response) {
		if (request instanceof ServerHandleInvocationMessage m)
		{
			try
			{
				System.out.println(m.getPayload());
				var jMessage = objectMapper.readTree(m.getPayload());
				var parts = jMessage.get(1).get("payload").get(0).toString().split("\\.");
				String payloadName = parts[parts.length - 1].replace("\"", "");

				var handler = handlerService.findByPayloadName(payloadName);
				switch (handler.getHandlerType())
				{
					case AggregateCommandHandler ->
					{
						var aggregateId = jMessage.get(1).get("aggregateId").toString().replace("\"", "");
						var lock = lockRegistry.obtain("AGGREGATE:" + aggregateId);
						lock.lock();
						try
						{
							var invocation = buildAggregateCommandHandlerInvocation(aggregateId, jMessage);
							messageBus.cast(
									messageBus.findNodeAddress(handler.getRanch().getName()),
									new ServiceHandleDomainCommandMessage(payloadName, invocation),
									resp -> {
										eventStore.publishEvent((String) resp, aggregateId);
										lock.unlock();
										response.sendResponse(resp);

									},
									error -> {
										lock.unlock();
										response.sendError(error.toThrowable());
									}

							);
						} catch (Exception e)
						{
							lock.unlock();
							throw e;
						}

					}
					case CommandHandler ->
					{
						var invocation = buildServiceCommandHandlerInvocation(jMessage);

						messageBus.cast(
								messageBus.findNodeAddress(handler.getRanch().getName()),
								new ServiceHandleServiceCommandMessage(payloadName, invocation),
								resp -> {
									eventStore.publishEvent((String) resp);
									response.sendResponse(resp);
								},
								error -> {
									response.sendError(error.toThrowable());
								}

						);
					}
					case QueryHandler ->
					{
						var invocation = buildQueryHandlerInvocation(jMessage);
						messageBus.cast(
								messageBus.findNodeAddress(handler.getRanch().getName()),
								new ServiceHandleQueryMessage(payloadName, invocation),
								response::sendResponse,
								error -> {
									response.sendError(error.toThrowable());
								}

						);
					}
					default ->
							response.sendError(new RuntimeException("Missing Handler " + ((ServerHandleInvocationMessage) request).getPayload()));
				}
			} catch (Exception e)
			{
				response.sendError(e);
			}
		} else
		{
			response.sendError(new IllegalArgumentException("Handler Not Found " + request));
		}
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
				var state = componentEventConsumingStateRepository
						.findById(entry.getKey())
						.orElse(new ComponentEventConsumingState(
								entry.getKey(),
								ranch.getName(),
								entry.getValue().get(0).getHandlerType() == HandlerType.SagaEventHandler ?
										eventStore.getLastEventSequenceNumber() : 0L));
				if (entry.getValue().get(0).getHandlerType() == HandlerType.EventHandler)
				{
					processProjectorEvents(new ArrayList<>(), entry.getValue(), eventStore, state, componentEventConsumingStateRepository);
				} else if (entry.getValue().get(0).getHandlerType() == HandlerType.SagaEventHandler)
				{
					// processSagaEvents(List.of(), entry.getValue(), eventStore, state, componentEventConsumingStateRepository, sagaStateRepository);
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
				//transactionManager.rollback(t);
			}
		}
	}

	private void processSagaEvents(
			List<PublishedEvent> events,
			List<Handler> handlers,
			EventStore eventStore,
			ComponentEventConsumingState state,
			ComponentEventConsumingStateRepository repository,
			SagaStateRepository sagaStateRepository) {
		retryBlock(() -> {
			while (events.isEmpty())
			{
				Thread.sleep(1000);
				events.addAll(eventStore.fetchEvents(state.getLastEventSequenceNumber()).stream().map(EventStoreEntry::toPublishedEvent).toList());
			}
			var event = events.get(0);

			var handler = handlers.stream()
					.filter(h -> h.getHandledPayload().getName().equals(event.getEventName())).findFirst();
			if (handler.isEmpty())
			{
				retryBlock(() -> {
					var newState = repository.save(new ComponentEventConsumingState(
							state.getComponentName(),
							state.getRanchName(),
							event.getEventSequenceNumber()));

					processSagaEvents(events.size() == 1 ? new ArrayList<>() : events.subList(1, events.size() - 1), handlers, eventStore, newState, repository, sagaStateRepository);
				});
			} else
			{
				var eventJson = objectMapper.readTree(event.getEventMessage());
				var associationProperty = handler.get().getAssociationProperty();
				var associationValue = eventJson.get(1).get("payload").get(1).get(associationProperty).toString().replace("\"", "");

				var sagaState = sagaStateRepository.getLastStatus(
								state.getComponentName(),
								associationProperty,
								associationValue)
						.orElse(new SagaState(state.getComponentName()));

				messageBus.cast(messageBus.findNodeAddress(state.getRanchName()),
						new ServiceHandleSagaEventMessage(
								event.getEventName(),
								state.getComponentName(),
								buildSagaEventHandlerInvocation(
										eventJson,
										sagaState.getCurrentState() == null ? null : objectMapper.readTree(sagaState.getCurrentState()))
						),
						resp -> {
							retryBlock(() -> {
								if (!objectMapper.readTree(resp.toString()).get(1).get("ended").asBoolean())
									sagaStateRepository.save(new SagaState(
											sagaState.getId(),
											sagaState.getSagaName(),
											resp.toString()
									));
								else
									sagaStateRepository.delete(sagaState);
								var newState = repository.save(new ComponentEventConsumingState(
										state.getComponentName(),
										state.getRanchName(),
										event.getEventSequenceNumber()));

								processSagaEvents(events.size() == 1 ? new ArrayList<>() : events.subList(1, events.size() - 1), handlers, eventStore, newState, repository, sagaStateRepository);
							});

						},
						error -> {
							error.toException().printStackTrace();
							processSagaEvents(events, handlers, eventStore, state, repository, sagaStateRepository);

						});
			}
		});

	}

	private void processProjectorEvents(
			List<PublishedEvent> events,
			List<Handler> handlers,
			EventStore eventStore,
			ComponentEventConsumingState state,
			ComponentEventConsumingStateRepository repository) {
		retryBlock(() -> {
			while (events.isEmpty())
			{
				Thread.sleep(1000);
				events.addAll(eventStore.fetchEvents(state.getLastEventSequenceNumber()).stream().map(EventStoreEntry::toPublishedEvent).toList());
			}
			var event = events.get(0);
			if (handlers.stream()
					.noneMatch(h -> h.getHandledPayload().getName().equals(event.getEventName())))
			{
				processNextProjectorEvent(events, handlers, eventStore, state, repository, event);
			}else{
				messageBus.cast(messageBus.findNodeAddress(state.getRanchName()),
						new ServiceHandleProjectorEventMessage(
								event.getEventName(),
								state.getComponentName(),
								buildProjectorEventHandlerInvocation(
										objectMapper.readTree(event.getEventMessage()))
						),
						resp -> {
							processNextProjectorEvent(events, handlers, eventStore, state, repository, event);
						},
						err -> {
							err.toException().printStackTrace();
							processProjectorEvents(events, handlers, eventStore, state, repository);
						});
			}


		});
	}

	private void processNextProjectorEvent(List<PublishedEvent> events, List<Handler> handlers, EventStore eventStore, ComponentEventConsumingState state, ComponentEventConsumingStateRepository repository, PublishedEvent event) {
		retryBlock(() -> {
			var newState = repository.save(new ComponentEventConsumingState(
					state.getComponentName(),
					state.getRanchName(),
					event.getEventSequenceNumber()));

			processProjectorEvents(events.size() == 1 ? new ArrayList<>() : events.subList(1, events.size() - 1), handlers, eventStore, newState, repository);
		});
	}


	private String buildServiceCommandHandlerInvocation(JsonNode serviceCommandMessage) {
		var invocation = JsonNodeFactory.instance.arrayNode();
		invocation.add("org.eventrails.modeling.messaging.invocation.ServiceCommandHandlerInvocation");
		var invocationBody = JsonNodeFactory.instance.objectNode();
		invocationBody.set("commandMessage", serviceCommandMessage);
		invocation.add(invocationBody);
		return invocation.toString();
	}

	private String buildQueryHandlerInvocation(JsonNode queryMessage) {
		var invocation = JsonNodeFactory.instance.arrayNode();
		invocation.add("org.eventrails.modeling.messaging.invocation.QueryHandlerInvocation");
		var invocationBody = JsonNodeFactory.instance.objectNode();
		invocationBody.set("queryMessage", queryMessage);
		invocation.add(invocationBody);
		return invocation.toString();
	}

	private String buildProjectorEventHandlerInvocation(JsonNode eventMessage) {
		var invocation = JsonNodeFactory.instance.arrayNode();
		invocation.add("org.eventrails.modeling.messaging.invocation.ProjectorEventHandlerInvocation");
		var invocationBody = JsonNodeFactory.instance.objectNode();
		invocationBody.set("eventMessage", eventMessage);
		invocation.add(invocationBody);
		return invocation.toString();
	}

	private String buildSagaEventHandlerInvocation(JsonNode eventMessage, JsonNode sagaState) {
		var invocation = JsonNodeFactory.instance.arrayNode();
		invocation.add("org.eventrails.modeling.messaging.invocation.SagaEventHandlerInvocation");
		var invocationBody = JsonNodeFactory.instance.objectNode();
		invocationBody.set("eventMessage", eventMessage);
		invocationBody.set("sagaState", sagaState);
		invocation.add(invocationBody);
		return invocation.toString();
	}

	private String buildAggregateCommandHandlerInvocation(String aggregateId, JsonNode domainCommandMessage) throws JsonProcessingException {
		var invocation = JsonNodeFactory.instance.arrayNode();
		invocation.add("org.eventrails.modeling.messaging.invocation.AggregateCommandHandlerInvocation");

		var invocationBody = JsonNodeFactory.instance.objectNode();
		invocationBody.set("commandMessage", domainCommandMessage);

		var snapshot = eventStore.fetchSnapshot(aggregateId);
		invocationBody.set("aggregateState", snapshot == null ? null :
				objectMapper.readTree(snapshot.getAggregateState()));

		var eventStreamNode = JsonNodeFactory.instance.arrayNode();
		eventStreamNode.add("java.util.ArrayList");

		var eventStream = JsonNodeFactory.instance.arrayNode();

		var aggregateStory = snapshot == null ? eventStore.fetchAggregateStory(aggregateId) : eventStore.fetchAggregateStory(aggregateId, snapshot.getAggregateSequenceNumber());
		for (EventStoreEntry eventStoreEntry : aggregateStory)
		{
			eventStream.add(objectMapper.readTree(eventStoreEntry.getEventMessage()));
		}
		eventStreamNode.add(eventStream);

		invocationBody.set("eventStream", eventStreamNode);

		invocation.add(invocationBody);

		return invocation.toString();
	}


	private List<NodeAddress> fetchDispatcherAddresses() {
		return this.messageBus.getAddresses(serverNodeName);
	}


}
