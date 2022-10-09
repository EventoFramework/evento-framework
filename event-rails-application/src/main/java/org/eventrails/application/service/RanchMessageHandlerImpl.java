package org.eventrails.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eventrails.application.EventRailsApplication;
import org.eventrails.application.reference.ProjectorReference;
import org.eventrails.modeling.messaging.invocation.*;
import org.eventrails.modeling.messaging.message.DomainEventMessage;
import org.eventrails.modeling.messaging.message.ServiceEventMessage;
import org.eventrails.modeling.ranch.RanchHandlerResponse;
import org.eventrails.modeling.ranch.RanchMessageHandler;
import org.eventrails.shared.ObjectMapperUtils;
import org.eventrails.shared.exceptions.HandlerNotFoundException;
import org.eventrails.shared.exceptions.ThrowableWrapper;

public class RanchMessageHandlerImpl implements RanchMessageHandler {

	private final ObjectMapper payloadMapper = ObjectMapperUtils.getPayloadObjectMapper();

	private final EventRailsApplication application;

	public RanchMessageHandlerImpl(EventRailsApplication application) {
		this.application = application;
	}

	@Override
	public String handleDomainCommand(String domainCommandName, String domainCommandPayload) throws Exception {
		var handler = application.getAggregateMessageHandlers().getOrDefault(domainCommandName, null);
		if (handler == null) throw new HandlerNotFoundException("No handler found for %s in %s".formatted(domainCommandName, application.getRanchName()));
		try
		{
			var hi = payloadMapper.readValue(domainCommandPayload, AggregateCommandHandlerInvocation.class);
			var event = handler.invoke(hi.getCommandMessage(), hi.getAggregateState(), hi.getEventStream(),
					application.getCommandGateway(),
					application.getQueryGateway()
			);
			return payloadMapper.writeValueAsString(new DomainEventMessage(event));
		} catch (Throwable e)
		{
			e.printStackTrace();
			throw new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace()).toException();
		}
	}

	@Override
	public String handleServiceCommand(String serviceCommandName, String serviceCommandPayload) throws Exception {
		var handler = application.getServiceMessageHandlers().getOrDefault(serviceCommandName, null);
		if (handler == null) throw new HandlerNotFoundException("No handler found for %s in %s".formatted(serviceCommandPayload, application.getRanchName()));

		try
		{
			var hi = payloadMapper.readValue(serviceCommandPayload, ServiceCommandHandlerInvocation.class);
			var event = handler.invoke(hi.getCommandMessage(),
					application.getCommandGateway(),
					application.getQueryGateway()
			);
			return payloadMapper.writeValueAsString(new ServiceEventMessage(event));
		} catch (Throwable e)
		{
			e.printStackTrace();
			throw new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace()).toException();
		}
	}

	@Override
	public String handleQuery(String queryName, String queryPayload) throws Exception {
		var handler = application.getProjectionMessageHandlers().getOrDefault(queryName, null);
		if (handler == null) throw new HandlerNotFoundException("No handler found for %s in %s".formatted(queryName, application.getRanchName()));

		try
		{
			var hi = payloadMapper.readValue(queryPayload, QueryHandlerInvocation.class);
			var result = handler.invoke(hi.getQueryMessage(),
					application.getCommandGateway(),
					application.getQueryGateway()
			);
			return payloadMapper.writeValueAsString(result);
		} catch (Throwable e)
		{
			e.printStackTrace();
			throw new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace()).toException();
		}
	}

	@Override
	public void handleProjectorEvent(String eventName, String projectorName, String eventPayload) throws Exception {
		var handlers = application.getProjectorMessageHandlers().getOrDefault(eventName, null);
		if (handlers == null) throw new HandlerNotFoundException("No handler found for %s in %s".formatted(eventName, application.getRanchName()));


		var handler = handlers.getOrDefault(projectorName, null);
		if (handler == null) throw new HandlerNotFoundException("No handler found for %s in %s".formatted(eventName, application.getRanchName()));


		try
		{
			var hi = payloadMapper.readValue(eventPayload, ProjectorEventHandlerInvocation.class);
			handler.begin();
			handler.invoke(hi.getEventMessage(),
					application.getCommandGateway(),
					application.getQueryGateway()
			);
			handler.commit();
		} catch (Throwable e)
		{
			handler.rollback();
			e.printStackTrace();
			throw new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace()).toException();
		}
	}

	@Override
	public void handleProjectorEvent(String eventName, String eventPayload) throws Exception {
		var handlers = application.getProjectorMessageHandlers().getOrDefault(eventName, null);
		if (handlers == null) throw new HandlerNotFoundException("No handler found for %s in %s".formatted(eventName, application.getRanchName()));


		try
		{
			for (ProjectorReference handler : handlers.values())
			{
				var hi = payloadMapper.readValue(eventPayload, ProjectorEventHandlerInvocation.class);
				handler.invoke(hi.getEventMessage(),
						application.getCommandGateway(),
						application.getQueryGateway()
				);
			}

		} catch (Throwable e)
		{
			e.printStackTrace();
			throw new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace()).toException();
		}
	}

	@Override
	public String handleSagaEvent(String eventName, String sagaName, String eventPayload) throws Exception {
		var handlers = application.getSagaMessageHandlers().getOrDefault(eventName, null);
		if (handlers == null) throw new HandlerNotFoundException("No handler found for %s in %s".formatted(eventName, application.getRanchName()));


		var handler = handlers.getOrDefault(sagaName, null);
		if (handlers == null) throw new HandlerNotFoundException("No handler found for %s in %s".formatted(eventName, application.getRanchName()));


		try
		{
			var hi = payloadMapper.readValue(eventPayload, SagaEventHandlerInvocation.class);
			var state = handler.invoke(hi.getEventMessage(),
					hi.getSagaState(),
					application.getCommandGateway(),
					application.getQueryGateway()
			);
			return payloadMapper.writeValueAsString(state);
		} catch (Throwable e)
		{
			e.printStackTrace();
			throw new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace()).toException();
		}
	}
}
