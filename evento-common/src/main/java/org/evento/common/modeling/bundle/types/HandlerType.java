package org.evento.common.modeling.bundle.types;

/**
 * This enum represents the different types of handlers that can be used.
 */
public enum HandlerType {
	/**
	 * This class represents an aggregate command handler.
	 *
	 * <p>
	 * An aggregate command handler is responsible for handling commands that are targeted at an aggregate.
	 * It processes the commands and invokes the appropriate methods on the aggregate to perform the required actions.
	 * </p>
	 *
	 * <p>
	 * The aggregate command handler can be of different types, represented by the {@link HandlerType} enum.
	 * </p>
	 */
	AggregateCommandHandler,
	/**
	 * The CommandHandler class is responsible for handling commands.
	 *
	 * <p>
	 * A command handler processes received commands and invokes the appropriate methods to perform the required actions.
	 * </p>
	 *
	 * <p>
	 * The command handler can belong to different types, represented by the {@link HandlerType} enum.
	 * Different types of command handlers can be used for different purposes, such as handling aggregate commands,
	 * executing queries, handling events, etc.
	 * </p>
	 */
	CommandHandler,
	/**
	 * This class represents a query handler.
	 * A query handler is responsible for handling queries and returning the result.
	 * Different query handlers can be used for different purposes, such as retrieving data from a database,
	 * invoking an external service, or performing calculations.
	 */
	QueryHandler,
	/**
	 * This interface represents an event handler.
	 *
	 * <p>
	 * An event handler is responsible for handling events that occur in a software system.
	 * It receives events as input and performs the required actions based on the event type and content.
	 * </p>
	 *
	 * <p>
	 * Implementations of this interface should define how the events are handled and what actions should be taken in response to each event.
	 * </p>
	 *
	 * <p>
	 * Different types of event handlers can be used for different purposes, such as updating data, triggering notifications, logging events, etc.
	 * The type of event handler is determined by the implementing class.
	 * </p>
	 *
	 * <p>
	 * The event handler can belong to different types, represented by the {@link HandlerType} enum.
	 * </p>
	 */
	EventHandler,
	/**
	 * EventSourcingHandler is responsible for handling events in an event sourcing architecture.
	 * It receives events as input and performs the required actions to update the state of the system.
	 *
	 * EventSourcingHandler is a type of EventHandler and belongs to the HandlerType EventSourcingHandler.
	 * Implementations of EventSourcingHandler should define how events are handled and what actions should be taken in response to each event.
	 *
	 * Example usage:
	 *
	 * EventSourcingHandler eventSourcingHandler = new EventSourcingHandler();
	 * eventSourcingHandler.handleEvent(event);
	 */
	EventSourcingHandler,

	InvocationHandler,
	/**
	 * This class represents a saga event handler.
	 *
	 * <p>
	 * A saga event handler is responsible for handling events that occur in a saga within a software system.
	 * It receives events as input and performs the required actions based on the event type and content, usually as part of a larger transactional process.
	 * </p>
	 *
	 * <p>
	 * Implementations of this class should define how the events are handled within the context of a saga and what actions should be taken in response to each event.
	 * </p>
	 *
	 * <p>
	 * The saga event handler can be used to orchestrate complex interactions between multiple services or components, ensuring that the desired business logic is executed consistently
	 * in a distributed system.
	 * </p>
	 *
	 * <p>
	 * This saga event handler can be of different types, represented by the {@link HandlerType} enum.
	 * </p>
	 *
	 * <p>
	 * Example usage:
	 * </p>
	 *
	 * <pre>{@code
	 * SagaEventHandler sagaEventHandler = new SagaEventHandler();
	 * sagaEventHandler.handleEvent(event);
	 * }</pre>
	 */
	SagaEventHandler
}
