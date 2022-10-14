package org.eventrails.application;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eventrails.application.reference.*;
import org.eventrails.application.server.jgroups.JGroupsCommandGateway;
import org.eventrails.application.server.jgroups.JGroupsQueryGateway;
import org.eventrails.application.service.RanchMessageHandlerImpl;
import org.eventrails.modeling.annotations.component.*;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.gateway.QueryGateway;
import org.eventrails.modeling.messaging.message.bus.*;
import org.eventrails.shared.messaging.JGroupsMessageBus;
import org.jgroups.Event;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Set;

public class EventRailsApplication {


	private static final Logger logger = LogManager.getLogger(EventRailsApplication.class);
	private final String basePackage;
	private final String ranchName;

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
			String clusterName,
			String serverName
	) throws Exception {
		this.basePackage = basePackage;
		this.ranchName = ranchName;

		JChannel jChannel = new JChannel() {
			@Override
			public Object up(Message msg) {
				System.out.println("UP MSG - " + msg);
				return super.up(msg);
			}

			@Override
			public Object up(Event evt) {
				System.out.println("UP EVT - " + evt);
				return super.up(evt);
			}

			@Override
			public Object down(Event evt) {
				System.out.println("DOWN EVT - " + evt);
				return super.down(evt);
			}

			@Override
			public Object down(Message evt) {
				System.out.println("DOWN MSG - " + evt);
				return super.down(evt);
			}
		};


		var ranchMessageHandler = new RanchMessageHandlerImpl(this);

		var messageBus = new JGroupsMessageBus(jChannel,
				message -> {

				},

				(request, response) -> {
					try
					{
						if (request instanceof ServiceHandleDomainCommandMessage m)
						{
							response.sendResponse(ranchMessageHandler.handleDomainCommand(m.getCommandName(), m.getPayload()));
						} else if(request instanceof ServiceHandleServiceCommandMessage m)
						{
							response.sendResponse(ranchMessageHandler.handleServiceCommand(m.getCommandName(), m.getPayload()));
						} else if(request instanceof ServiceHandleQueryMessage m)
						{
							response.sendResponse(ranchMessageHandler.handleQuery(m.getQueryName(), m.getPayload()));
						}else if(request instanceof ServiceHandleProjectorEventMessage m)
						{
							ranchMessageHandler.handleProjectorEvent(m.getEventName(), m.getProjectorName(), m.getPayload());
							response.sendResponse(null);
						}else if(request instanceof ServiceHandleSagaEventMessage m)
						{
							response.sendResponse(ranchMessageHandler.handleSagaEvent(m.getEventName(),m.getSagaName(), m.getPayload()));
						}else{
							response.sendError(new IllegalArgumentException("Request not found"));
						}
					} catch (Throwable e)
					{
						response.sendError(e);
					}
				});
		jChannel.setName(ranchName);
		jChannel.connect(clusterName);

		commandGateway = new JGroupsCommandGateway(messageBus, serverName);
		queryGateway = new JGroupsQueryGateway(messageBus, serverName);

	}

	public static EventRailsApplication start(String basePackage, String ranchName, String messageChannelName, String serverName, String[] args) {
		try
		{
			EventRailsApplication eventRailsApplication = new EventRailsApplication(basePackage, ranchName, messageChannelName, serverName);
			logger.info("Parsing package");
			eventRailsApplication.parsePackage();
			logger.info("Server Started");
			return eventRailsApplication;
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}

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

	public static class ApplicationInfo {
		public String basePackage;
		public String ranchName;
		public String clusterName;

		public Set<String> aggregateMessageHandlers;
		public Set<String> serviceMessageHandlers;
		public Set<String> projectionMessageHandlers;
		public Set<String> projectorMessageHandlers;
		public Set<String> sagaMessageHandlers;
	}

	public ApplicationInfo getAppInfo() {
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
