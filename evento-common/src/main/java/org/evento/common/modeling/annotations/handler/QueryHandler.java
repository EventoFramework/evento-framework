package org.evento.common.modeling.annotations.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The QueryHandler annotation is a marker annotation used to mark methods as query handlers.
 * Query handlers are methods that handle specific query requests in a software system.
 * They are discovered and executed based on the presence of the query handler annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Handler
public @interface QueryHandler {
}
