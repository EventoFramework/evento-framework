package com.evento.common.modeling.annotations.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Handler annotation is a marker annotation used to mark other annotations as handlers.
 * Handlers are methods or classes that handle specific actions or events in a software system.
 * They are discovered and executed based on the presence of the handler annotation.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Handler {
}
