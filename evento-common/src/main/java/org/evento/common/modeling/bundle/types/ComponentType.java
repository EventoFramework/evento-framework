package org.evento.common.modeling.bundle.types;

/**
 * The ComponentType enum represents the different types of components in the system.
 * It is used to categorize and identify various roles or characteristics of classes in the RECQ component pattern.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 *     ComponentType componentType = ComponentType.Service;
 * </pre>
 *
 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern">RECQ Component Pattern</a>
 */
public enum ComponentType {
	/**
	 * The Aggregate class represents a group of related domain objects that are treated as a single unit.
	 * It encapsulates the business logic and data validation rules for the domain objects it contains.
	 *
	 * <p>
	 * Example usage:
	 * </p>
	 *
	 * <pre>
	 *     Aggregate aggregate = new Aggregate();
	 * </pre>
	 *
	 * @see ComponentType#Aggregate
	 */
	Aggregate,
	/**
	 * The Service class represents a service in the system.
	 * It encapsulates the business logic and exposes the functionality that other components can use.
	 *
	 * <p>
	 * Example usage:
	 * </p>
	 *
	 * <pre>
	 *     Service service = new Service();
	 * </pre>
	 *
	 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern">RECQ Component Pattern</a>
	 * @see ComponentType
	 */
	Service,
	/**
	 * The Projector class is a component in the RECQ component pattern.
	 * It is responsible for receiving events and updating projections based on the event data.
	 *
	 * <p>
	 * Example usage:
	 * </p>
	 *
	 * <pre>
	 *     Projector projector = new Projector();
	 * </pre>
	 *
	 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern">RECQ Component Pattern</a>
	 * @see ComponentType
	 */
	Projector,
	/**
	 * The Projection class represents a component in the RECQ component pattern.
	 * It is responsible for maintaining a read-only view of the data in the system.
	 * Projections are typically updated by Projectors based on events.
	 *
	 * <p>
	 * Example usage:
	 * </p>
	 *
	 * <pre>
	 *     Projection projection = new Projection();
	 * </pre>
	 *
	 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern">RECQ Component Pattern</a>
	 * @see ComponentType
	 */
	Projection,
	/**
	 * The Invoker class represents a component in the RECQ component pattern.
	 * It is responsible for invoking commands and dispatching events to the appropriate components.
	 *
	 * <p>
	 * Example usage:
	 * </p>
	 *
	 * <pre>
	 *     Invoker invoker = new Invoker();
	 * </pre>
	 *
	 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern">RECQ Component Pattern</a>
	 * @see ComponentType
	 */
	Invoker,
	/**
	 * The Observer class represents a component in the RECQ component pattern.
	 * It is responsible for observing changes or events in the system and taking action based on those changes.
	 *
	 * <p>
	 * Example usage:
	 * </p>
	 *
	 * <pre>
	 *     Observer observer = new Observer();
	 * </pre>
	 *
	 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern">RECQ Component Pattern</a>
	 * @see ComponentType
	 */
	Observer,

	Saga
}
