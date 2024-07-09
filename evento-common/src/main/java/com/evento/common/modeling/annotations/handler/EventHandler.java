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
    /**
     * The retry method is used to specify the number of retries to attempt when executing a specific action.
     * This method is an annotation attribute, typically used in conjunction with the EventHandler annotation.
     *
     * @return the number of retries to attempt. The default value is -1, indicating no specific retry count.
     */
    public int retry() default -1;
    /**
     * The retryDelay method is used to specify the delay in milliseconds between each retry attempt
     * when executing a specific action.
     * This method is an annotation attribute, typically used in conjunction with the EventHandler annotation.
     *
     * @return the delay in milliseconds between each retry attempt. The default value is 1000,
     * indicating no specific retry delay.
     */
    public int retryDelay() default 1000;
}
