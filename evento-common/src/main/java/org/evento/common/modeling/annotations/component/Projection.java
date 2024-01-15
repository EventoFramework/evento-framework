package org.evento.common.modeling.annotations.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation serves as a marker for classes that are considered a Projection.
 * Projections are used to handle queries by defining methods annotated with the {@link QueryHandler} annotation.
 * This annotation is used in conjunction with other annotations such as {@link Service}, {@link Observer},
 * {@link Projector}, {@link Saga}, and {@link Invoker} to categorize and identify various roles or
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
 *     private final DemoMongoRepository demoMongoRepository;
 *
 *     public DemoProjection(DemoMongoRepository demoMongoRepository) {
 *         this.demoMongoRepository = demoMongoRepository;
 *     }
 *
 *     @QueryHandler
 *     Single<DemoView> query(DemoViewFindByIdQuery query, QueryMessage<DemoViewFindByIdQuery> queryMessage) {
 *         // Handle query logic
 *     }
 *
 *     @QueryHandler
 *     Multiple<DemoView> query(DemoViewFindAllQuery query) {
 *         // Handle query logic
 *     }
 *
 *     @QueryHandler
 *     Single<DemoRichView> queryRich(DemoRichViewFindByIdQuery query) {
 *         // Handle query logic
 *     }
 *
 *     @QueryHandler
 *     Multiple<DemoRichView> queryRich(DemoRichViewFindAllQuery query) {
 *         // Handle query logic
 *     }
 * }
 * }
 * </pre>
 *
 * @see Service
 * @see Observer
 * @see Projector
 * @see Saga
 * @see Invoker
 * @see QueryHandler
 * @see View
 * @see QueryMessage
 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern/projection">Projection in RECQ Component Patterns</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface Projection {
}
