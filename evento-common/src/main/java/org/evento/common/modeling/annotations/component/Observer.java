package org.evento.common.modeling.annotations.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Observer annotation is a marker annotation that designates a class as an observer.
 * Observers are used to handle events by defining methods annotated with the {@link EventHandler} annotation.
 * This annotation is used in conjunction with other annotations such as {@link Service}, {@link Projection},
 * {@link Saga}, and {@link Projector} to categorize and identify various roles or characteristics of classes
 * within a software system.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {@code
 * @Observer
 * public class DemoObserver {
 *
 *     @EventHandler
 *     public void on(DemoUpdatedEvent event, CommandGateway commandGateway) {
 *         // Handle event logic
 *     }
 *
 *     @EventHandler
 *     public void on(DemoDeletedEvent event, CommandGateway commandGateway) {
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
 * @see EventHandler
 * @see ObserverManager
 * @see ObserverReference
 * @see <a></a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface Observer {
}
