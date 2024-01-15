package org.evento.common.modeling.annotations.component;

import org.evento.common.utils.Context;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Projector annotation is a marker annotation that designates a class as a projector.
 * Projectors are used to handle events by defining methods annotated with the {@link EventHandler} annotation.
 * This annotation is used in conjunction with other annotations such as {@link Service}, {@link Observer},
 * {@link Projection}, {@link Saga}, and {@link Invoker} to categorize and identify various roles or
 * characteristics of classes within a software system.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {@code
 * @Projector(version = 3)
 * public class DemoMysqlProjector {
 *
 *     private final DemoMysqlRepository demoMysqlRepository;
 *
 *     public DemoMysqlProjector(DemoMysqlRepository demoMysqlRepository) {
 *         this.demoMysqlRepository = demoMysqlRepository;
 *     }
 *
 *     @EventHandler
 *     void on(DemoCreatedEvent event, QueryGateway queryGateway, EventMessage eventMessage) {
 *         // Handle event logic
 *     }
 *
 *     @EventHandler
 *     void on(DemoUpdatedEvent event) {
 *         // Handle event logic
 *     }
 *
 *     @EventHandler
 *     void on(DemoDeletedEvent event) {
 *         // Handle event logic
 *     }
 * }
 * }
 * </pre>
 *
 * @see Service
 * @see Observer
 * @see Projection
 * @see Saga
 * @see Invoker
 * @see EventHandler
 * @see ProjectorManager
 * @see ProjectorReference
 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern/projector">Projector in RECQ Component Patterns</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface Projector {
	int version();
	String[] context() default {Context.ALL};
}
