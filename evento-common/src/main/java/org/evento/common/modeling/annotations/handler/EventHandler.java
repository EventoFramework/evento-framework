package org.evento.common.modeling.annotations.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The EventHandler annotation is used to mark methods as event handlers.
 * Event handlers are methods that handle specific events.
 * They are identified by the EventHandler annotation and can be registered in event sourcing frameworks.
 * When an event is published, the framework will invoke the corresponding event handler method.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Handler
public @interface EventHandler {
}
