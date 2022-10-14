package org.eventrails.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eventrails.application.EventRailsApplication;
import org.eventrails.modeling.messaging.invocation.*;
import org.eventrails.modeling.messaging.message.DomainEventMessage;
import org.eventrails.modeling.messaging.message.ServiceEventMessage;
import org.eventrails.modeling.ranch.RanchMessageHandler;
import org.eventrails.shared.ObjectMapperUtils;
import org.eventrails.modeling.exceptions.HandlerNotFoundException;

public class RanchMessageHandlerImpl implements RanchMessageHandler {

	private final ObjectMapper payloadMapper = ObjectMapperUtils.getPayloadObjectMapper();

	private final EventRailsApplication application;

	public RanchMessageHandlerImpl(EventRailsApplication application) {
		this.application = application;
	}

	@Override
	public String handleDomainCommand(String domainCommandName, String domainCommandPayload) throws Throwable {
		var handler = application.getAggregateMessageHandlers().getOrDefault(domainCommandName, null);
		if (handler == null)
			throw new HandlerNotFoundException("No handler found for %s in %s".formatted(domainCommandName, application.getRanchName()));
		var hi = payloadMapper.readValue(domainCommandPayload, AggregateCommandHandlerInvocation.class);
		var event = handler.invoke(hi.getCommandMessage(), hi.getAggregateState(), hi.getEventStream(),
				application.getCommandGateway(),
				application.getQueryGateway()
		);
		return payloadMapper.writeValueAsString(new DomainEventMessage(event));

	}

	@Override
	public String handleServiceCommand(String serviceCommandName, String serviceCommandPayload) throws Throwable {
		var handler = application.getServiceMessageHandlers().getOrDefault(serviceCommandName, null);
		if (handler == null)
			throw new HandlerNotFoundException("No handler found for %s in %s".formatted(serviceCommandPayload, application.getRanchName()));
		var hi = payloadMapper.readValue(serviceCommandPayload, ServiceCommandHandlerInvocation.class);
		var event = handler.invoke(hi.getCommandMessage(),
				application.getCommandGateway(),
				application.getQueryGateway()
		);
		return payloadMapper.writeValueAsString(new ServiceEventMessage(event));

	}

	@Override
	public String handleQuery(String queryName, String queryPayload) throws Throwable {
		var handler = application.getProjectionMessageHandlers().getOrDefault(queryName, null);
		if (handler == null)
			throw new HandlerNotFoundException("No handler found for %s in %s".formatted(queryName, application.getRanchName()));
		var hi = payloadMapper.readValue(queryPayload, QueryHandlerInvocation.class);
		var result = handler.invoke(hi.getQueryMessage(),
				application.getCommandGateway(),
				application.getQueryGateway()
		);
		return payloadMapper.writeValueAsString(result);

	}

	@Override
	public void handleProjectorEvent(String eventName, String projectorName, String eventPayload) throws Throwable {
		var handlers = application.getProjectorMessageHandlers().getOrDefault(eventName, null);
		if (handlers == null)
			throw new HandlerNotFoundException("No handler found for %s in %s".formatted(eventName, application.getRanchName()));


		var handler = handlers.getOrDefault(projectorName, null);
		if (handler == null)
			throw new HandlerNotFoundException("No handler found for %s in %s".formatted(eventName, application.getRanchName()));


		var hi = payloadMapper.readValue(eventPayload, ProjectorEventHandlerInvocation.class);
		handler.begin();
		handler.invoke(hi.getEventMessage(),
				application.getCommandGateway(),
				application.getQueryGateway()
		);
		handler.commit();

	}


	@Override
	public String handleSagaEvent(String eventName, String sagaName, String eventPayload) throws Throwable {
		var handlers = application.getSagaMessageHandlers().getOrDefault(eventName, null);
		if (handlers == null)
			throw new HandlerNotFoundException("No handler found for %s in %s".formatted(eventName, application.getRanchName()));


		var handler = handlers.getOrDefault(sagaName, null);
		if (handler == null)
			throw new HandlerNotFoundException("No handler found for %s in %s".formatted(eventName, application.getRanchName()));


		var hi = payloadMapper.readValue(eventPayload, SagaEventHandlerInvocation.class);
		var state = handler.invoke(hi.getEventMessage(),
				hi.getSagaState(),
				application.getCommandGateway(),
				application.getQueryGateway()
		);
		return payloadMapper.writeValueAsString(state);

	}
}
