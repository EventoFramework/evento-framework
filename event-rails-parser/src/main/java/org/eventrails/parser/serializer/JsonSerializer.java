package org.eventrails.parser.serializer;

import com.google.gson.*;
import org.eventrails.parser.model.RanchApplicationDescription;
import org.eventrails.parser.model.node.*;
import org.eventrails.parser.model.handler.*;
import org.eventrails.parser.model.payload.Payload;
import org.eventrails.parser.model.payload.PayloadDescription;
import org.eventrails.parser.model.payload.Query;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JsonSerializer {
	private final Gson gson;

	public JsonSerializer() {
		gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().serializeNulls().create();
	}

	public String serialize(RanchApplicationDescription ranchApplicationDescription) {
		JsonArray components = new JsonArray();
		for (Node node : ranchApplicationDescription.getNodes())
		{
			JsonObject jComponent = new JsonObject();
			jComponent.addProperty("type", node.getClass().getSimpleName());
			jComponent.addProperty("name", node.getComponentName());

			if (node instanceof Saga c)
			{
				jComponent.add("handlers", serializeHandlers(c.getSagaEventHandlers()));
			}else if(node instanceof Aggregate c){
				jComponent.add("handlers", serializeHandlers(
						Stream.concat(c.getAggregateCommandHandlers().stream(), c.getEventSourcingHandlers().stream()).collect(Collectors.toList())));
			}else if(node instanceof Projector c){
				jComponent.add("handlers", serializeHandlers(c.getEventHandlers()));
			}else if(node instanceof Projection c){
				jComponent.add("handlers", serializeHandlers(c.getQueryHandlers()));
			}else if(node instanceof Service c){
				jComponent.add("handlers", serializeHandlers(c.getCommandHandlers()));
			}


			components.add(jComponent);
		}

		JsonArray payloads = new JsonArray();
		for (PayloadDescription payloadDescription : ranchApplicationDescription.getPayloadDescriptions())
		{
			JsonObject payload = new JsonObject();
			payload.addProperty("name", payloadDescription.getName());
			payload.addProperty("type", payloadDescription.getType());
			payload.add("schema", payloadDescription.getSchema());
			payloads.add(payload);

		}

		var appDesc = new JsonObject();
		appDesc.add("components", components);
		appDesc.add("payloads", payloads);
		return gson.toJson(appDesc);
	}

	private JsonElement serializeHandlers(List<? extends Handler<?>> handlers) {
		JsonArray jsonArray = new JsonArray();
		for (Handler<?> handler : handlers)
		{
			JsonObject jHandler = new JsonObject();
			jHandler.addProperty("type", handler.getClass().getSimpleName());
			jHandler.addProperty("payload", handler.getPayload().getName());
			if(handler instanceof AggregateCommandHandler h){
				jHandler.addProperty("returnType", h.getProducedEvent().getName());
			}else if(handler instanceof ServiceCommandHandler h && h.getProducedEvent() != null){
				jHandler.addProperty("returnType", h.getProducedEvent().getName());
			}else if(handler instanceof QueryHandler h){
				JsonObject jResponseType = new JsonObject();
				jResponseType.addProperty("type", h.getPayload().getReturnType().getClass().getSimpleName());
				jResponseType.addProperty("view", h.getPayload().getReturnType().getViewName());
				jHandler.add("returnType",jResponseType);
			}else{
				jHandler.add("returnType", null);
			}
			HashSet<Payload> invocations = new HashSet<>();
			if(handler instanceof HasCommandInvocations h){
				invocations.addAll(h.getCommandInvocations());
			}
			if(handler instanceof HasQueryInvocations h){
				invocations.addAll(h.getQueryInvocations());
			}
			jHandler.add("invocations", serializeInvocations(invocations));
			jsonArray.add(jHandler);
		}
		return jsonArray;
	}

	private JsonElement serializeInvocations(HashSet<Payload> invocations) {
		JsonArray jsonArray = new JsonArray();
		for (Payload invocation : invocations)
		{
			JsonObject jInvocation = new JsonObject();
			jInvocation.addProperty("type", invocation.getClass().getSimpleName());
			jInvocation.addProperty("name", invocation.getName());
			if(invocation instanceof Query q){
				JsonObject jResponseType = new JsonObject();
				jResponseType.addProperty("type", q.getReturnType().getClass().getSimpleName());
				jResponseType.addProperty("view", q.getReturnType().getViewName());
				jInvocation.add("responseType",jResponseType);
			}
			jsonArray.add(jInvocation);
		}
		return jsonArray;
	}
}
