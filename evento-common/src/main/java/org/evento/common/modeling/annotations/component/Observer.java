package org.evento.common.modeling.annotations.component;

import org.evento.common.utils.Context;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * The Observer annotation serves as a marker for classes that are considered observers.
 * Observers are responsible for observing events and reacting to them.
 * This annotation is used in conjunction with other annotations such as {@link Service}, {@link Projection},
 * {@link Saga}, {@link Projector}, and {@link Invoker} to categorize and identify various roles or
 * characteristics of classes within a software system.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {@code
 * @Observer(version = 2, context = {Context.ALL})
 * public class DemoObserver {
 *
 *     private final DemoService demoService;
 *
 *     public DemoObserver(DemoService demoService) {
 *         this.demoService = demoService;
 *     }
 *
 *     @EventHandler
 *     public void on(DemoEvent event) {
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
 * @see Projection
 * @see Saga
 * @see Projector
 * @see Invoker
 * @see org.evento.common.modeling.annotations.handler.EventHandler
 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern/observer">Observer in RECQ Component Patterns</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface Observer {
    /**
     * Returns the version of the observer.
     *
     * @return the version of the observer
     */
    int version();
    /**
     * Retrieves the context options for the given method context.
     *
     * @return the context options for the method context
     */
    String[] context() default {Context.ALL};
}
