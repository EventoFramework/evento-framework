package org.eventrails.application;

import org.eventrails.application.reference.*;
import org.eventrails.application.server.jgroups.JGroupsCommandGateway;
import org.eventrails.application.server.jgroups.JGroupsQueryGateway;
import org.eventrails.modeling.annotations.component.*;
import org.eventrails.modeling.exceptions.HandlerNotFoundException;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.gateway.QueryGateway;
import org.eventrails.modeling.messaging.message.DecoratedDomainCommandMessage;
import org.eventrails.modeling.messaging.message.*;
import org.eventrails.modeling.messaging.query.SerializedQueryResponse;
import org.eventrails.modeling.state.SerializedAggregateState;
import org.eventrails.modeling.state.SerializedSagaState;
import org.eventrails.shared.messaging.AutoscalingProtocol;
import org.eventrails.shared.messaging.JGroupsMessageBus;
import org.jgroups.JChannel;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class EventRailsApplication {

	private final String basePackage;
	private final String bundleName;
	private final JGroupsMessageBus messageBus;

	private HashMap<String, AggregateReference> aggregateMessageHandlers = new HashMap<>();
	private HashMap<String, ServiceReference> serviceMessageHandlers = new HashMap<>();
	private HashMap<String, ProjectionReference> projectionMessageHandlers = new HashMap<>();
	private HashMap<String, HashMap<String, ProjectorReference>> projectorMessageHandlers = new HashMap<>();
	private HashMap<String, HashMap<String, SagaReference>> sagaMessageHandlers = new HashMap<>();

	private transient AutoscalingProtocol autoscalingProtocol;

	private boolean suffering = false;

	private transient CommandGateway commandGateway;
	private transient QueryGateway queryGateway;

	private EventRailsApplication(
			String basePackage,
			String bundleName,
			String clusterName,
			String serverName
	) throws Exception {


		this.basePackage = basePackage;
		this.bundleName = bundleName;

		JChannel jChannel = new JChannel();

		messageBus = new JGroupsMessageBus(jChannel,
				message -> {

				},
				(request, response) -> {
					try
					{
						this.autoscalingProtocol.arrival();
						Thread.sleep(5000);
						if (request instanceof DecoratedDomainCommandMessage c)
						{
							var handler = getAggregateMessageHandlers()
									.get(c.getCommandMessage().getCommandName());
							if (handler == null)
								throw new HandlerNotFoundException("No handler found for %s in %s"
										.formatted(c.getCommandMessage().getCommandName(), getBundleName()));
							var envelope = new AggregateStateEnvelope(c.getSerializedAggregateState().getAggregateState());
							var event = handler.invoke(
									c.getCommandMessage(),
									envelope,
									c.getEventStream(),
									commandGateway,
									queryGateway
							);
							response.sendResponse(
									new DomainCommandResponseMessage(
											new DomainEventMessage(event),
											handler.getSnapshotFrequency() <= c.getEventStream().size() ?
													new SerializedAggregateState<>(envelope.getAggregateState()) : null
									)
							);
						} else if (request instanceof ServiceCommandMessage c)
						{
							var handler = getServiceMessageHandlers().get(c.getCommandName());
							if (handler == null)
								throw new HandlerNotFoundException("No handler found for %s in %s"
										.formatted(c.getCommandName(), getBundleName()));
							var event = handler.invoke(
									c,
									commandGateway,
									queryGateway
							);
							response.sendResponse(event);
						} else if (request instanceof QueryMessage<?> q)
						{
							var handler = getProjectionMessageHandlers().get(q.getQueryName());
							if (handler == null)
								throw new HandlerNotFoundException("No handler found for %s in %s".formatted(q.getQueryName(), getBundleName()));
							var result = handler.invoke(
									q,
									commandGateway,
									queryGateway
							);
							response.sendResponse(new SerializedQueryResponse<>(result));
						} else if (request instanceof EventToProjectorMessage m)
						{
							var handlers = getProjectorMessageHandlers()
									.get(m.getEventMessage().getEventName());
							if (handlers == null)
								throw new HandlerNotFoundException("No handler found for %s in %s"
										.formatted(m.getEventMessage().getEventName(), getBundleName()));


							var handler = handlers.getOrDefault(m.getProjectorName(), null);
							if (handler == null)
								throw new HandlerNotFoundException("No handler found for %s in %s"
										.formatted(m.getEventMessage().getEventName(), getBundleName()));

							handler.begin();
							handler.invoke(
									m.getEventMessage(),
									commandGateway,
									queryGateway
							);
							handler.commit();
							response.sendResponse(null);
						} else if (request instanceof EventToSagaMessage m)
						{
							var handlers = getSagaMessageHandlers()
									.get(m.getEventMessage().getEventName());
							if (handlers == null)
								throw new HandlerNotFoundException("No handler found for %s in %s"
										.formatted(m.getEventMessage().getEventName(), getBundleName()));


							var handler = handlers.getOrDefault(m.getSagaName(), null);
							if (handler == null)
								throw new HandlerNotFoundException("No handler found for %s in %s"
										.formatted(m.getEventMessage().getEventName(), getBundleName()));


							var state = handler.invoke(
									m.getEventMessage(),
									m.getSerializedSagaState().getSagaState(),
									commandGateway,
									queryGateway
							);
							response.sendResponse(new SerializedSagaState<>(state));
						} else
						{
							throw new IllegalArgumentException("Request not found");
						}
					} catch (Throwable e)
					{
						response.sendError(e);
					}finally
					{
						this.autoscalingProtocol.departure();
					}

				});

		this.autoscalingProtocol = new AutoscalingProtocol(
				bundleName,
				serverName,
				messageBus,
				16,
				4,
				5,
				5);

		jChannel.setName(bundleName);
		jChannel.connect(clusterName);



		commandGateway = new JGroupsCommandGateway(messageBus, serverName);
		queryGateway = new JGroupsQueryGateway(messageBus, serverName);

	}

	public void startBus() throws Exception {
		messageBus.enableBus();
	}

	public void stopBus() throws Exception {
		messageBus.disableBus();
	}

	public static EventRailsApplication start(String basePackage, String bundleName, String messageChannelName, String serverName, String[] args) {
		try
		{
			EventRailsApplication eventRailsApplication = new EventRailsApplication(basePackage, bundleName, messageChannelName, serverName);
			eventRailsApplication.parsePackage();
			eventRailsApplication.startBus();
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
			var aggregateReference = new AggregateReference(aClass.getConstructor().newInstance(), aClass.getAnnotation(Aggregate.class).snapshotFrequency());
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

	public String getBundleName() {
		return bundleName;
	}

	public static class ApplicationInfo {
		public String basePackage;
		public String bundleName;
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
		info.bundleName = bundleName;
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
