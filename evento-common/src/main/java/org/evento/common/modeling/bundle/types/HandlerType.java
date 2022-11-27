package org.evento.common.modeling.bundle.types;

public enum HandlerType {
	AggregateCommandHandler,
	CommandHandler,
	QueryHandler,
	EventHandler,
	EventSourcingHandler,
	InvocationHandler,
	SagaEventHandler
}
