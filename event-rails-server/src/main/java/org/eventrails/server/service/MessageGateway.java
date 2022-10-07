package org.eventrails.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.eventrails.modeling.ranch.RanchMessageHandler;
import org.eventrails.server.domain.model.Ranch;
import org.eventrails.server.es.EventStore;
import org.eventrails.server.es.eventstore.EventStoreEntry;
import org.eventrails.shared.exceptions.NodeNotFoundException;
import org.eventrails.shared.exceptions.ThrowableWrapper;
import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;
import org.jgroups.blocks.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class MessageGateway implements RequestHandler {
	private final HandlerService handlerService;

	private final LockRegistry lockRegistry;

	private final ObjectMapper objectMapper;

	private final EventStore eventStore;

	private final RpcDispatcher rpcDispatcher;

	@Value("${eventrails.cluster.node.dispatcher.name}")
	private String dispatcherName;

	public MessageGateway(HandlerService handlerService, LockRegistry lockRegistry, ObjectMapper objectMapper, EventStore eventStore, RpcDispatcher rpcDispatcher) {
		this.handlerService = handlerService;
		this.lockRegistry = lockRegistry;
		this.objectMapper = objectMapper;
		this.eventStore = eventStore;
		this.rpcDispatcher = rpcDispatcher;
		rpcDispatcher.setRequestHandler(this);

	}

	@Override
	public Object handle(Message msg) throws Exception {
		try
		{
			var jMessage = objectMapper.readTree(msg.getObject().toString());
			var parts = jMessage.get(1).get("payload").get(0).toString().split("\\.");
			String payloadName = parts[parts.length - 1].replace("\"", "");

			var handler = handlerService.findByPayloadName(payloadName);
			switch (handler.getHandlerType())
			{
				case AggregateCommandHandler ->
				{
					var aggregateId = jMessage.get(1).get("aggregateId").toString().replace("\"","");
					var lock = lockRegistry.obtain(aggregateId);
					try
					{
						lock.lock();

						var invocation = buildAggregateCommandHandlerInvocation(aggregateId, jMessage);
						var eventMessage = invokeDomainCommand(handler.getRanch(), payloadName, invocation);
						var eventEntry = eventStore.publishEvent(eventMessage, aggregateId);
						rpcDispatcher.castMessage(
								fetchDispatcherAddresses(),
								new ObjectMessage(null, eventEntry.toPublishedEvent()),
								RequestOptions.ASYNC()
						);
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
					rpcDispatcher.castMessage(
							fetchDispatcherAddresses(),
							new ObjectMessage(null, eventEntry.toPublishedEvent()),
							RequestOptions.ASYNC()
					);
					return eventMessage;
				}
				case QueryHandler ->
				{
					var invocation = buildQueryHandlerInvocation(jMessage);
					return invokeQuery(handler.getRanch(), payloadName, invocation);
				}
			}
		}catch (Exception e){
			e.printStackTrace();
			throw new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace()).toException();
		}
		throw new RuntimeException("Missing Handler");
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

		var aggregateStory = snapshot == null ? eventStore.fetchAggregateStory(aggregateId) :  eventStore.fetchAggregateStory(aggregateId, snapshot.getAggregateSequenceNumber());
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
						RanchMessageHandler.class.getMethod("handleDomainCommand",String.class, String.class),
						commandName,
						invocation),
				RequestOptions.SYNC()
		);

	}

	private String invokeQuery(Ranch ranch, String queryName, String invocation) throws Exception {
		return rpcDispatcher.callRemoteMethod(
				fetchRanchAddress(ranch),
				new MethodCall(
						RanchMessageHandler.class.getMethod("handleQuery",String.class, String.class),
						queryName,
						invocation),
				RequestOptions.SYNC()
		);
	}

	private String invokeServiceCommand(Ranch ranch, String commandName, String invocation) throws Exception {
		return rpcDispatcher.callRemoteMethod(
				fetchRanchAddress(ranch),
				new MethodCall(
						RanchMessageHandler.class.getMethod("handleServiceCommand",String.class, String.class),
						commandName,
						invocation),
				RequestOptions.SYNC()
		);
	}
	private Address fetchRanchAddress(Ranch ranch) {
		return this.rpcDispatcher.getChannel()
				.getView().getMembers().stream()
				.filter(address -> ranch.getName().equals(address.toString()))
				.findAny()
				.orElseThrow(() ->  new NodeNotFoundException("Node %s not found".formatted(ranch.getName())));
	}

	private Collection<Address> fetchDispatcherAddresses() {
		return this.rpcDispatcher.getChannel()
				.getView().getMembers().stream()
				.filter(address -> dispatcherName.equals(address.toString()))
				.toList();
	}


}
