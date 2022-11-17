package org.eventrails.application;

import org.eventrails.application.reference.*;
import org.eventrails.common.modeling.annotations.component.*;
import org.eventrails.common.modeling.messaging.message.application.*;
import org.eventrails.common.modeling.exceptions.HandlerNotFoundException;
import org.eventrails.common.modeling.messaging.query.SerializedQueryResponse;
import org.eventrails.common.modeling.state.SerializedAggregateState;
import org.eventrails.common.modeling.state.SerializedSagaState;
import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.messaging.gateway.CommandGateway;
import org.eventrails.common.messaging.gateway.QueryGateway;
import org.eventrails.common.performance.AutoscalingProtocol;
import org.eventrails.common.utils.Inject;
import org.reflections.Reflections;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;

public class EventRailsApplication {

	private final String basePackage;
	private final String bundleName;

	private HashMap<String, AggregateReference> aggregateMessageHandlers = new HashMap<>();
	private HashMap<String, ServiceReference> serviceMessageHandlers = new HashMap<>();
	private HashMap<String, ProjectionReference> projectionMessageHandlers = new HashMap<>();
	private HashMap<String, HashMap<String, ProjectorReference>> projectorMessageHandlers = new HashMap<>();
	private HashMap<String, HashMap<String, SagaReference>> sagaMessageHandlers = new HashMap<>();
	private transient CommandGateway commandGateway;
	private transient QueryGateway queryGateway;

	private EventRailsApplication(
			String basePackage,
			String bundleName,
			String serverName,
			MessageBus messageBus,
			AutoscalingProtocol autoscalingProtocol){


		this.basePackage = basePackage;
		this.bundleName = bundleName;
		this.commandGateway = new CommandGateway(messageBus, serverName);
		this.queryGateway = new QueryGateway(messageBus, serverName);

		messageBus.setRequestReceiver((request, response) -> {
			try
			{
				autoscalingProtocol.arrival();
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
					response.sendResponse(new ServiceEventMessage(event));
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
				autoscalingProtocol.departure();
			}

		});

	}

	public static EventRailsApplication start(
			String basePackage,
			String bundleName,
			String serverName,
			MessageBus messageBus,
			AutoscalingProtocol autoscalingProtocol) {


		try
		{
			EventRailsApplication eventRailsApplication = new EventRailsApplication(basePackage, bundleName, serverName, messageBus, autoscalingProtocol);
			eventRailsApplication.parsePackage(clz->null);
			messageBus.enableBus();
			return eventRailsApplication;
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}

	}

	public static EventRailsApplication start(
			String basePackage,
			String bundleName,
			String serverName,
			MessageBus messageBus,
			AutoscalingProtocol autoscalingProtocol,
			Function<Class<?>, Object> findInjectableObject) {


		try
		{
			EventRailsApplication eventRailsApplication = new EventRailsApplication(basePackage, bundleName, serverName, messageBus, autoscalingProtocol);
			eventRailsApplication.parsePackage(findInjectableObject);
			messageBus.enableBus();
			return eventRailsApplication;
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}

	}

	private void parsePackage(Function<Class<?>, Object> findInjectableObject) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {

		Reflections reflections = new Reflections(basePackage);
		for (Class<?> aClass : reflections.getTypesAnnotatedWith(Aggregate.class))
		{
			var aggregateReference = new AggregateReference(createComponentInstance(aClass, findInjectableObject), aClass.getAnnotation(Aggregate.class).snapshotFrequency());
			for (String command : aggregateReference.getRegisteredCommands())
			{
				aggregateMessageHandlers.put(command, aggregateReference);
			}
		}
		for (Class<?> aClass : reflections.getTypesAnnotatedWith(Service.class))
		{
			var serviceReference = new ServiceReference(createComponentInstance(aClass, findInjectableObject));
			for (String command : serviceReference.getRegisteredCommands())
			{
				serviceMessageHandlers.put(command, serviceReference);
			}
		}
		for (Class<?> aClass : reflections.getTypesAnnotatedWith(Projection.class))
		{
			var projectionReference = new ProjectionReference(createComponentInstance(aClass, findInjectableObject));
			for (String query : projectionReference.getRegisteredQueries())
			{
				projectionMessageHandlers.put(query, projectionReference);
			}
		}
		for (Class<?> aClass : reflections.getTypesAnnotatedWith(Projector.class))
		{
			var projectorReference = new ProjectorReference(createComponentInstance(aClass, findInjectableObject));
			for (String event : projectorReference.getRegisteredEvents())
			{
				var hl = projectorMessageHandlers.getOrDefault(event, new HashMap<>());
				hl.put(aClass.getSimpleName(), projectorReference);
				projectorMessageHandlers.put(event, hl);
			}
		}
		for (Class<?> aClass : reflections.getTypesAnnotatedWith(Saga.class))
		{
			var sagaReference = new SagaReference(createComponentInstance(aClass, findInjectableObject));
			for (String event : sagaReference.getRegisteredEvents())
			{
				var hl = sagaMessageHandlers.getOrDefault(event, new HashMap<>());
				hl.put(aClass.getSimpleName(), sagaReference);
				sagaMessageHandlers.put(event, hl);
			}
		}
	}

	private Object createComponentInstance(Class<?> aClass, Function<Class<?>, Object> findInjectableObject) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
		var ref =  aClass.getConstructor().newInstance();
		for (Field declaredField : aClass.getDeclaredFields()) {
			if(declaredField.getAnnotation(Inject.class)!=null){
				var oldAccessibility = declaredField.canAccess(ref);
				declaredField.setAccessible(true);
				declaredField.set(ref, findInjectableObject.apply(declaredField.getType()));
				declaredField.setAccessible(oldAccessibility);
			}
		}
		return ref;
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
