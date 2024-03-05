package com.evento.common.modeling.annotations.component;

import com.evento.common.utils.Context;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * The Projector annotation is a marker annotation that designates a class as a projector.
 * Projectors are responsible for handling events and updating the projection state.
 * This annotation is used in conjunction with other annotations such as {@link Service}, {@link Observer},
 * {@link Saga}, {@link Projection}, and {@link Invoker} to categorize and identify various roles or
 * characteristics of classes within a software system.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {@code
 * @Projector(version = 1, context = {Context.ALL})
 * public class DemoProjector {
 *
 *     @EventHandler
 *     public void on(DemoCreatedEvent event) {
 *         // Handle event logic
 *     }
 *
 *     @EventHandler
 *     public void on(DemoUpdatedEvent event) {
 *         // Handle event logic
 *     }
 * }
 * }
 * </pre>
 *
 * @see Service
 * @see Observer
 * @see Saga
 * @see Projection
 * @see Invoker
 * @see com.evento.common.modeling.annotations.handler.EventHandler
 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern/projector">Projector in RECQ Component Patterns</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface Projector {
	/**
	 * Returns the version of the Projector.
	 *
	 * @return the version of the Projector
	 */
	int version();
}
