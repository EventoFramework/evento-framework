package org.eventrails.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.gateway.QueryGateway;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;

public class EventRailsApplication {

	private final String basePackage;

	private HashMap<String, AggregateCommandHandlerReference> commandHandlers = new HashMap<>();

	private CommandGateway commandGateway;
	private QueryGateway queryGateway;

	private EventRailsApplication(String basePackage) {
		this.basePackage = basePackage;
	}

	public static void start(String basePackage, String[] args) {
		EventRailsApplication eventRailsApplication = new EventRailsApplication(basePackage);
		eventRailsApplication.parsePackage();
	}

	private void parsePackage() {

	}

	public JsonObject executeAggregateCommand(String commandName, JsonObject commandMessage, JsonObject aggregateState, JsonArray eventStream){
		try
		{
			return commandHandlers.get(commandName).invoke(commandMessage, aggregateState, eventStream, commandGateway, queryGateway);
		} catch (InvocationTargetException | IllegalAccessException e)
		{
			throw new RuntimeException(e);
		}
	}


}
