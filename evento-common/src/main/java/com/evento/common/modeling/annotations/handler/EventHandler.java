package com.evento.common.modeling.annotations.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;



/**
 * The EventHandler annotation is used to mark methods as event handlers.
 * Event handlers are methods that handle specific events in a software system.
 * They are discovered and executed based on the presence of the EventHandler annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Handler
public @interface EventHandler {
}
