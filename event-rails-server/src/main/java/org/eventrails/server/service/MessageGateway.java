package org.eventrails.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eventrails.modeling.messaging.invocation.AggregateCommandHandlerInvocation;
import org.eventrails.server.domain.model.types.HandlerType;
import org.springframework.stereotype.Service;

@Service
public class MessageGateway {
	private final HandlerService handlerService;
	private final RanchService ranchService;

	private final ObjectMapper objectMapper;

	public MessageGateway(HandlerService handlerService, RanchService ranchService, ObjectMapper objectMapper) {
		this.handlerService = handlerService;
		this.ranchService = ranchService;
		this.objectMapper = objectMapper;
	}

	public void handle(String messagePayload) throws JsonProcessingException {

		var jsonArray = objectMapper.readTree(messagePayload);
		var parts = jsonArray.get(1).get("payload").get(0).toString().split("\\.");
		String payloadName = parts[parts.length-1].replace("\"","");

		var handler = handlerService.findByPayloadName(payloadName);
		switch (handler.getHandlerType()){
			case AggregateCommandHandler -> {
				var invocation = JsonNodeFactory.instance.arrayNode();
				invocation.add("org.eventrails.modeling.messaging.invocation.AggregateCommandHandlerInvocation");
				var invocationBody = JsonNodeFactory.instance.objectNode();
				invocationBody.set("commandMessage", jsonArray);
				invocationBody.set("aggregateState", null);
				var eventStream = JsonNodeFactory.instance.arrayNode();
				eventStream.add("java.util.ArrayList");
				eventStream.add(JsonNodeFactory.instance.arrayNode());
				invocationBody.set("eventStream",eventStream);


				invocation.add(invocationBody);
			}
			case CommandHandler -> {}
			case QueryHandler -> {}
		}



	}
}
