package com.evento.common.modeling.annotations.component;

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

	/**
	 * Whether the bundle must wait for this projector's consumer to reach the head
	 * of the event stream before enabling itself on the cluster.
	 *
	 * <p>When {@code false} (the default) the bundle enables immediately after
	 * registration: the application starts serving commands and queries while this
	 * projector keeps aligning in the background (a {@code Projector head reached}
	 * log line marks the moment it catches up). When {@code true} the bundle stays
	 * disabled — and therefore invisible to the cluster — until this projector has
	 * caught up, so queries never observe a read model known to be behind.
	 *
	 * @return true when bundle enablement must wait for this projector's alignment
	 */
	boolean waitForHeadReached() default false;
}
