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
	Aggregate,
	Service,
	Projector,
	Projection,
	Invoker,
	Observer,
	Saga
}
