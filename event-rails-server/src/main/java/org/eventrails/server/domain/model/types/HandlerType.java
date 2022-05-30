package org.eventrails.server.domain.model.types;

public enum HandlerType {
	AggregateCommandHandler,
	CommandHandler,
	QueryHandler,
	EventHandler,
	EventSourcingHandler,
	SagaEventHandler
}
