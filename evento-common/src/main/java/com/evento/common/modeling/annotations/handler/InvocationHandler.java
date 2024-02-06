package com.evento.common.modeling.annotations.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code InvocationHandler} annotation is a marker annotation used to mark methods or classes as handlers.
 * Handlers are methods or classes that handle specific actions or events in a software system.
 * They are discovered and executed based on the presence of the {@code InvocationHandler} annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Handler
public @interface InvocationHandler {
}
