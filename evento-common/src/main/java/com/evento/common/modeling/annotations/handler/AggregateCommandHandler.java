package com.evento.common.modeling.annotations.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark a method as an aggregate command handler.
 *
 * @see com.evento.common.modeling.annotations.component.Aggregate
 * @see <a href="https://docs.eventoframework.com/evento-framework/component/aggregate/aggregatecommandhandler">AggregateCommandHandler</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Handler
public @interface AggregateCommandHandler {
	/**
	 * Checks if the method is marked as an aggregate initializer.
	 *
	 * @return {@code true} if the method is an initializer, otherwise {@code false}.
	 */
	boolean init() default false;
}
