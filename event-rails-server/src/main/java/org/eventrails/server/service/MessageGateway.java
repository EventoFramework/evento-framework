package org.eventrails.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eventrails.server.es.EventStore;
import org.eventrails.server.es.eventstore.EventStoreEntry;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;

@Service
public class MessageGateway {
	private final HandlerService handlerService;
	private final RanchService ranchService;

	private final LockRegistry lockRegistry;

	private final ObjectMapper objectMapper;

	private final EventStore eventStore;

	public MessageGateway(HandlerService handlerService, RanchService ranchService, LockRegistry lockRegistry, ObjectMapper objectMapper, EventStore eventStore) {
		this.handlerService = handlerService;
		this.ranchService = ranchService;
		this.lockRegistry = lockRegistry;
		this.objectMapper = objectMapper;
		this.eventStore = eventStore;
	}

	public Mono<String> handle(String messagePayload) throws Throwable {

		var jMessage = objectMapper.readTree(messagePayload);
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
						var eventMessage = ranchService.invokeDomainCommand(handler.getRanch(), payloadName, invocation);
						return Mono.fromFuture(eventMessage.thenApplyAsync(message -> {
							eventStore.publishEvent(message, aggregateId);
							return message;
						}));

					} finally
					{
						lock.unlock();
					}
			}
			case CommandHandler ->
			{
				var invocation = buildServiceCommandHandlerInvocation(jMessage);
				var eventMessage = ranchService.invokeServiceCommand(handler.getRanch(), payloadName, invocation);
				return Mono.fromFuture(eventMessage.thenApplyAsync(message -> {
					if(!message.equals("null"))
						eventStore.publishEvent(message);
					return message;
				}));
			}
			case QueryHandler ->
			{
				var invocation = buildQueryHandlerInvocation(jMessage);
				return Mono.fromFuture(ranchService.invokeQuery(handler.getRanch(), payloadName, invocation));
			}
		}

		return Mono.just(null);
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
}
