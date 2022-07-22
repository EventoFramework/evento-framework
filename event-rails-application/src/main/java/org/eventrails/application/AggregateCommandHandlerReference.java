package org.eventrails.application;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.gateway.QueryGateway;
import org.eventrails.modeling.messaging.CommandMessage;
import org.eventrails.modeling.messaging.payload.Command;
import org.eventrails.modeling.messaging.payload.DomainCommand;
import org.eventrails.modeling.messaging.payload.Event;
import org.eventrails.modeling.state.AggregateState;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class AggregateCommandHandlerReference {

	private final Method commandHandler;

	private final AggregateReference aggregateRef;

	private final Gson gson = new Gson();

	public AggregateCommandHandlerReference(AggregateReference aggregateRef, Method commandHandler) {
		this.commandHandler = commandHandler;
		this.aggregateRef = aggregateRef;
	}

	public Event invoke(JsonObject commandMessage, JsonObject aggregateState, JsonArray eventStream, CommandGateway commandGateway, QueryGateway queryGateway) throws InvocationTargetException, IllegalAccessException {
		Object[] args = new Object[]{commandHandler.getParameterCount()};
		var cm = gson.fromJson(commandMessage, CommandMessage.class);
		AggregateState lastState = null;
		for (JsonElement jsonElement : eventStream)
		{
			JsonObject jO = jsonElement.getAsJsonObject();
			var eventName = jO.get("eventName").getAsString();
			var eventPayload = jO.get("payload").getAsJsonObject();
			var eh = aggregateRef.getEventSourcingHandler(eventName);
			lastState = (AggregateState) eh.invoke(aggregateRef.getRef(), gson.fromJson(eventPayload, eh.getParameterTypes()[0]), gson.fromJson(eventPayload, eh.getParameterTypes()[1]));
		}
		for (int i = 0; i < commandHandler.getParameterCount(); i++)
		{
			var param = commandHandler.getParameters()[i];
			if (param.getType().isAssignableFrom(Command.class))
			{
				args[i] = gson.fromJson(commandMessage.get("payload").getAsJsonObject(), param.getType());
			} else if (param.getType().isAssignableFrom(AggregateState.class))
			{
				if (lastState == null)
					args[i] = gson.fromJson(aggregateState, param.getType());
				else
					args[i] = aggregateState;
			} else if (param.getType().isAssignableFrom(CommandGateway.class))
			{
				args[i] = commandGateway;
			} else if (param.getType().isAssignableFrom(QueryGateway.class))
			{
				args[i] = queryGateway;
			}else if (param.getType().isAssignableFrom(CommandMessage.class))
			{
				args[i] = cm;
			}
		}
		return (Event) commandHandler.invoke(aggregateRef.getRef(), args);
	}
}
