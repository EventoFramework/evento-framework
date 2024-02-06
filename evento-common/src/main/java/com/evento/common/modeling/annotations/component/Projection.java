package com.evento.common.modeling.annotations.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * The Projection annotation is a marker annotation that designates a class as a projection.
 * Projections are used to read and transform data from the event store and update the projection state.
 * This annotation is used in conjunction with other annotations such as {@link Service}, {@link Observer},
 * {@link Saga}, {@link Projector}, and {@link Invoker} to categorize and identify various roles or
 * characteristics of classes within a software system.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {@code
 * @Projection
 * public class DemoProjection {
 *
 *     @EventHandler
 *     void on(DemoEvent event) {
 *         // Handle event logic
 *     }
 *
 *     @QueryHandler
 *     List<DemoSummary> handle(FindAllDemosQuery query) {
 *         // Handle query logic
 *     }
 * }
 * }
 * </pre>
 *
 * @see Service
 * @see Observer
 * @see Saga
 * @see Projector
 * @see Invoker
 * @see com.evento.common.modeling.annotations.handler.EventHandler
 * @see com.evento.common.modeling.annotations.handler.QueryHandler
 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern/projection">Projection in RECQ Component Patterns</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface Projection {
}
