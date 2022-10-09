package org.eventrails.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.eventrails.modeling.gateway.MessageGateway;
import org.eventrails.modeling.gateway.PublishedEvent;
import org.eventrails.modeling.ranch.RanchMessageHandler;
import org.eventrails.server.domain.model.ComponentEventConsumingState;
import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.model.Ranch;
import org.eventrails.server.domain.repository.RanchRepository;
import org.eventrails.server.domain.repository.ComponentEventConsumingStateRepository;
import org.eventrails.server.es.EventStore;
import org.eventrails.server.es.eventstore.EventStoreEntry;
import org.eventrails.shared.exceptions.NodeNotFoundException;
import org.eventrails.shared.exceptions.ThrowableWrapper;
import org.jgroups.Address;
import org.jgroups.blocks.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

@Service
public class MessageGatewayService implements MessageGateway {
	private final HandlerService handlerService;

	private final LockRegistry lockRegistry;

	private final ObjectMapper objectMapper;

	private final EventStore eventStore;

	private final RpcDispatcher rpcDispatcher;
	private final Thread eventConsumer;
	private final LinkedBlockingDeque<PublishedEvent> eventQueue = new LinkedBlockingDeque<>();

	private final RanchRepository ranchRepository;

	private final ComponentEventConsumingStateRepository componentEventConsumingStateRepository;


	@Value("${eventrails.cluster.node.server.name}")
	private String serverNodeName;


	public MessageGatewayService(HandlerService handlerService, LockRegistry lockRegistry, ObjectMapper objectMapper, EventStore eventStore, RpcDispatcher rpcDispatcher, RanchRepository ranchRepository, ComponentEventConsumingStateRepository componentEventConsumingStateRepository) {
		this.handlerService = handlerService;
		this.lockRegistry = lockRegistry;
		this.objectMapper = objectMapper;
		this.eventStore = eventStore;
		this.rpcDispatcher = rpcDispatcher;
		this.ranchRepository = ranchRepository;
		this.componentEventConsumingStateRepository = componentEventConsumingStateRepository;
		rpcDispatcher.setServerObject(this);
		eventConsumer = new Thread(this::eventConsumer);
		eventConsumer.start();
	}

	public void eventConsumer() {
		while (true)
		{
			try
			{
				var event = eventQueue.take();

				// Responsibility Algorithm
				var dispatcherAddresses = fetchDispatcherAddresses();
				var nodeIndex = dispatcherAddresses.indexOf(rpcDispatcher.getChannel().getAddress());
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
					var lock = lockRegistry.obtain("RANCH:" + ranch.getName());
					if (lock.tryLock())
					{
						try
						{
							for (Map.Entry<String, List<Handler>> entry : handlerService.findAllEventHandlersByRanch(ranch)
									.stream().collect(groupingBy(Handler::getComponentName)).entrySet())
							{
								var state = componentEventConsumingStateRepository
										.findById(entry.getKey())
										.orElse(new ComponentEventConsumingState(
												entry.getKey(),
												0L,
												null));
								var leftEvents = List.of(event);
								if (event.getEventSequenceNumber() > state.getLastEventSequenceNumber() + 1)
								{
									leftEvents = eventStore.fetchEvents(state.getLastEventSequenceNumber()).stream().map(EventStoreEntry::toPublishedEvent).collect(Collectors.toList());
								} else if (event.getEventSequenceNumber() <= state.getLastEventSequenceNumber())
								{
									leftEvents = List.of();
								}
								for (PublishedEvent leftEvent : leftEvents)
								{
									var handler = entry.getValue().stream()
											.filter(h -> h.getHandledPayload().getName().equals(leftEvent.getEventName())).findFirst();
									if (handler.isPresent())
									{
										switch (handler.get().getHandlerType())
										{
											case EventHandler ->
											{
												invokeEventHandler(
														ranch,
														state.getComponentName(),
														leftEvent.getEventName(),
														buildProjectorEventHandlerInvocation(
																objectMapper.readTree(leftEvent.getEventMessage()))
												);
												break;
											}
											case SagaEventHandler ->
											{
												var newSagaState = invokeSagaEventHandler(
														ranch,
														state.getComponentName(),
														leftEvent.getEventName(),
														buildSagaEventHandlerInvocation(
																objectMapper.readTree(leftEvent.getEventMessage()),
																state.getCurrentState() == null ? null : objectMapper.readTree(state.getCurrentState())));
												state.setCurrentState(newSagaState);
												break;
											}
										}
									}

									state.setLastEventSequenceNumber(leftEvent.getEventSequenceNumber());
									state = componentEventConsumingStateRepository.save(state);
								}

							}


						} catch (Exception e)
						{
							e.printStackTrace();
						} finally
						{
							lock.unlock();
						}
					}
				}
			}catch (Exception e){
				e.printStackTrace();
			}

		}
	}


	@Override
	public String handleInvocation(String payload) throws Exception {
		try
		{
			System.out.println(payload);
			var jMessage = objectMapper.readTree(payload);
			var parts = jMessage.get(1).get("payload").get(0).toString().split("\\.");
			String payloadName = parts[parts.length - 1].replace("\"", "");

			var handler = handlerService.findByPayloadName(payloadName);
			switch (handler.getHandlerType())
			{
				case AggregateCommandHandler ->
				{
					var aggregateId = jMessage.get(1).get("aggregateId").toString().replace("\"", "");
					var lock = lockRegistry.obtain("AGGREGATE:" + aggregateId);
					try
					{
						lock.lock();

						var invocation = buildAggregateCommandHandlerInvocation(aggregateId, jMessage);
						var eventMessage = invokeDomainCommand(handler.getRanch(), payloadName, invocation);
						var eventEntry = eventStore.publishEvent(eventMessage, aggregateId);
						for (Address address : fetchDispatcherAddresses())
						{
							rpcDispatcher.callRemoteMethod(address,
									new MethodCall(
											MessageGateway.class.getMethod("handleEvent", PublishedEvent.class),
											eventEntry.toPublishedEvent()
									), RequestOptions.ASYNC());
						}
						return eventMessage;

					} finally
					{
						lock.unlock();
					}
				}
				case CommandHandler ->
				{
					var invocation = buildServiceCommandHandlerInvocation(jMessage);
					var eventMessage = invokeServiceCommand(handler.getRanch(), payloadName, invocation);
					var eventEntry = eventStore.publishEvent(eventMessage);
					for (Address address : fetchDispatcherAddresses())
					{
						rpcDispatcher.callRemoteMethod(address,
								new MethodCall(
										MessageGateway.class.getMethod("handleEvent", PublishedEvent.class),
										eventEntry.toPublishedEvent()
								), RequestOptions.ASYNC());
					}
					return eventMessage;
				}
				case QueryHandler ->
				{
					var invocation = buildQueryHandlerInvocation(jMessage);
					return invokeQuery(handler.getRanch(), payloadName, invocation);
				}
			}
		} catch (Exception e)
		{
			e.printStackTrace();
			throw new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace()).toException();
		}
		throw new RuntimeException("Missing Handler");
	}

	@Override
	public void handleEvent(PublishedEvent event) throws Exception {
		eventQueue.put(event);
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

	private String invokeDomainCommand(Ranch ranch, String commandName, String invocation) throws Exception {

		return rpcDispatcher.callRemoteMethod(
				fetchRanchAddress(ranch),
				new MethodCall(
						RanchMessageHandler.class.getMethod("handleDomainCommand", String.class, String.class),
						commandName,
						invocation),
				RequestOptions.SYNC()
		);

	}

	private String invokeQuery(Ranch ranch, String queryName, String invocation) throws Exception {
		return rpcDispatcher.callRemoteMethod(
				fetchRanchAddress(ranch),
				new MethodCall(
						RanchMessageHandler.class.getMethod("handleQuery", String.class, String.class),
						queryName,
						invocation),
				RequestOptions.SYNC()
		);
	}


	private String invokeServiceCommand(Ranch ranch, String commandName, String invocation) throws Exception {
		return rpcDispatcher.callRemoteMethod(
				fetchRanchAddress(ranch),
				new MethodCall(
						RanchMessageHandler.class.getMethod("handleServiceCommand", String.class, String.class),
						commandName,
						invocation),
				RequestOptions.SYNC()
		);
	}

	private String invokeSagaEventHandler(Ranch ranch, String sagaName, String eventName, String eventMessage) throws Exception {
		return rpcDispatcher.callRemoteMethod(
				fetchRanchAddress(ranch),
				new MethodCall(
						RanchMessageHandler.class.getMethod("handleSagaEvent", String.class,  String.class, String.class),
						eventName,
						sagaName,
						eventMessage),
				RequestOptions.SYNC()
		);
	}

	private void invokeEventHandler(Ranch ranch, String eventName, String eventMessage) throws Exception {
		rpcDispatcher.callRemoteMethod(
				fetchRanchAddress(ranch),
				new MethodCall(
						RanchMessageHandler.class.getMethod("handleProjectorEvent", String.class, String.class),
						eventName,
						eventMessage),
				RequestOptions.SYNC()
		);
	}	private void invokeEventHandler(Ranch ranch, String projectorName,  String eventName, String eventMessage) throws Exception {
		rpcDispatcher.callRemoteMethod(
				fetchRanchAddress(ranch),
				new MethodCall(
						RanchMessageHandler.class.getMethod("handleProjectorEvent", String.class, String.class, String.class),
						eventName,
						projectorName,
						eventMessage),
				RequestOptions.SYNC()
		);
	}

	private Address fetchRanchAddress(Ranch ranch) {
		return this.rpcDispatcher.getChannel()
				.getView().getMembers().stream()
				.filter(address -> ranch.getName().equals(address.toString()))
				.findAny()
				.orElseThrow(() -> new NodeNotFoundException("Node %s not found".formatted(ranch.getName())));
	}

	private List<Address> fetchDispatcherAddresses() {
		return this.rpcDispatcher.getChannel()
				.getView().getMembers().stream()
				.filter(address -> serverNodeName.equals(address.toString()))
				.sorted()
				.toList();
	}


}
