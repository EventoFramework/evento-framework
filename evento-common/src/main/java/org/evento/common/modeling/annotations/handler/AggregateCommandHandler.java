package org.evento.common.modeling.annotations.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark a method as an aggregate command handler.
 *
 * @see org.evento.common.modeling.annotations.component.Aggregate
 * @see <a href="https://docs.eventoframework.com/evento-framework/component/aggregate/aggregatecommandhandler">AggregateCommandHandler</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Handler
public @interface AggregateCommandHandler {
	boolean init() default false;
}
