package org.evento.common.modeling.bundle.types;

/**
 * This enum represents the different types of handlers that can be used.
 */
public enum HandlerType {
	AggregateCommandHandler,
	CommandHandler,
	QueryHandler,
	EventHandler,
	EventSourcingHandler,
	InvocationHandler,
	SagaEventHandler
}
