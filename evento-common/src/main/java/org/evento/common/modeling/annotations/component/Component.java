package org.evento.common.modeling.annotations.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation serves as a marker for classes that are considered RECQ components.
 * Components are used to categorize and identify various roles or characteristics
 * of classes within a software system.
 *
 * @see Service
 * @see Projection
 * @see Saga
 * @see Observer
 * @see Projector
 * @see Invoker
 * @see Aggregate
 * @see EventHandler
 * @see QueryHandler
 *
 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern">RECQ Component Pattern</a>
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Component {
}
