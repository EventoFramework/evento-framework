package org.evento.common.modeling.annotations.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Aggregate annotation is used to mark a class as an aggregate in a software system.
 * Aggregates are used to represent a cluster of associated objects that are treated as a single unit.
 * This annotation is a part of the RECQ component pattern and is used in conjunction with other annotations such as
 * {@link Service}, {@link Projection}, {@link Saga}, {@link Observer}, {@link Projector}, and {@link Invoker} to categorize and
 * identify various roles or characteristics of classes.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {@code
 * @Aggregate
 * public class DemoAggregate {
 *
 *     @AggregateCommandHandler
 *     public DemoCreatedEvent handle(DemoCreateCommand command) {
 *         // Handle the command logic
 *     }
 *
 *     @EventSourcingHandler
 *     public DemoAggregateState on(DemoCreatedEvent event) {
 *         // Apply the event to the aggregate state
 *     }
 * }
 * }
 * </pre>
 *
 * @see Service
 * @see Projection
 * @see Saga
 * @see Observer
 * @see Projector
 * @see Invoker
 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern/aggregate">Aggregate in RECQ Component Pattern</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface Aggregate {
	/**
	 * Retrieves the snapshot frequency for an annotated aggregate class.
	 *
	 * @return The snapshot frequency for the aggregate class. Returns -1 if no snapshot frequency is specified.
	 */
	int snapshotFrequency() default -1;
}
