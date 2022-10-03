package org.eventrails.application;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eventrails.application.reference.*;
import org.eventrails.application.server.ApplicationServer;
import org.eventrails.modeling.annotations.component.*;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.gateway.QueryGateway;
import org.jgroups.*;
import org.jgroups.stack.IpAddress;
import org.reflections.Reflections;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class EventRailsApplication {


	private static final Logger logger = LogManager.getLogger(EventRailsApplication.class);
	private final String basePackage;
	private final String ranchName;
	private final ApplicationServer applicationServer;

	private HashMap<String, AggregateReference> aggregateMessageHandlers = new HashMap<>();
	private HashMap<String, ServiceReference> serviceMessageHandlers = new HashMap<>();
	private HashMap<String, ProjectionReference> projectionMessageHandlers = new HashMap<>();
	private HashMap<String, HashMap<String, ProjectorReference>> projectorMessageHandlers = new HashMap<>();
	private HashMap<String, HashMap<String, SagaReference>> sagaMessageHandlers = new HashMap<>();

	private transient CommandGateway commandGateway;
	private transient QueryGateway queryGateway;

	private EventRailsApplication(
			String basePackage,
			String ranchName,
			String clusterUrls,
			int serverPort
	) throws IOException {
		this.basePackage = basePackage;
		this.ranchName = ranchName;
		commandGateway = new CommandGatewayImpl(clusterUrls);
		this.applicationServer = new ApplicationServer(serverPort, this);
	}

	public static EventRailsApplication start(String basePackage, String ranchName, String clusterUrls, int serverPort, String[] args) {
		try
		{
			EventRailsApplication eventRailsApplication = new EventRailsApplication(basePackage, ranchName, clusterUrls, serverPort);
			logger.info("Parsing package");
			eventRailsApplication.parsePackage();
			logger.info("Server starting on port {}", serverPort);
			eventRailsApplication.startServer();
			logger.info("Server Started");
			return eventRailsApplication;
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}

	}

	private void startServer() throws Exception {
		applicationServer.start();

	}
	private void parsePackage() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {

		Reflections reflections = new Reflections(basePackage);
		for (Class<?> aClass : reflections.getTypesAnnotatedWith(Aggregate.class))
		{
			var aggregateReference = new AggregateReference(aClass.getConstructor().newInstance());
			for (String command : aggregateReference.getRegisteredCommands())
			{
				aggregateMessageHandlers.put(command, aggregateReference);
			}
		}
		for (Class<?> aClass : reflections.getTypesAnnotatedWith(Service.class))
		{
			var serviceReference = new ServiceReference(aClass.getConstructor().newInstance());
			for (String command : serviceReference.getRegisteredCommands())
			{
				serviceMessageHandlers.put(command, serviceReference);
			}
		}
		for (Class<?> aClass : reflections.getTypesAnnotatedWith(Projection.class))
		{
			var projectionReference = new ProjectionReference(aClass.getConstructor().newInstance());
			for (String query : projectionReference.getRegisteredQueries())
			{
				projectionMessageHandlers.put(query, projectionReference);
			}
		}
		for (Class<?> aClass : reflections.getTypesAnnotatedWith(Projector.class))
		{
			var projectorReference = new ProjectorReference(aClass.getConstructor().newInstance());
			for (String event : projectorReference.getRegisteredEvents())
			{
				var hl = projectorMessageHandlers.getOrDefault(event, new HashMap<>());
				hl.put(aClass.getSimpleName(), projectorReference);
				projectorMessageHandlers.put(event, hl);
			}
		}
		for (Class<?> aClass : reflections.getTypesAnnotatedWith(Saga.class))
		{
			var sagaReference = new SagaReference(aClass.getConstructor().newInstance());
			for (String event : sagaReference.getRegisteredEvents())
			{
				var hl = sagaMessageHandlers.getOrDefault(event, new HashMap<>());
				hl.put(aClass.getSimpleName(), sagaReference);
				sagaMessageHandlers.put(event, hl);
			}
		}
	}


	public HashMap<String, AggregateReference> getAggregateMessageHandlers() {
		return aggregateMessageHandlers;
	}

	public HashMap<String, ServiceReference> getServiceMessageHandlers() {
		return serviceMessageHandlers;
	}

	public HashMap<String, ProjectionReference> getProjectionMessageHandlers() {
		return projectionMessageHandlers;
	}


	public HashMap<String, HashMap<String, ProjectorReference>> getProjectorMessageHandlers() {
		return projectorMessageHandlers;
	}

	public HashMap<String, HashMap<String, SagaReference>> getSagaMessageHandlers() {
		return sagaMessageHandlers;
	}

	public String getBasePackage() {
		return basePackage;
	}

	public String getRanchName() {
		return ranchName;
	}

	public static class ApplicationInfo{
		public String basePackage;
		public String ranchName;
		public String clusterName;

		public Set<String> aggregateMessageHandlers;
		public Set<String> serviceMessageHandlers;
		public Set<String> projectionMessageHandlers;
		public Set<String> projectorMessageHandlers;
		public Set<String> sagaMessageHandlers;
	}

	public ApplicationInfo getAppInfo(){
		var info = new ApplicationInfo();
		info.basePackage = basePackage;
		info.ranchName = ranchName;
		info.aggregateMessageHandlers = aggregateMessageHandlers.keySet();
		info.serviceMessageHandlers = serviceMessageHandlers.keySet();
		info.projectionMessageHandlers = projectionMessageHandlers.keySet();
		info.projectorMessageHandlers = projectorMessageHandlers.keySet();
		info.sagaMessageHandlers = sagaMessageHandlers.keySet();
		return info;
	}

	public CommandGateway getCommandGateway() {
		return commandGateway;
	}

	public QueryGateway getQueryGateway() {
		return queryGateway;
	}
}
