package org.evento.common.modeling.annotations.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SagaEventHandler is an annotation that marks a method as an event handler for a saga.
 * It should be used in combination with the @Handler annotation to indicate that the method
 * handles a specific type of event in a software system.
 *
 * The SagaEventHandler annotation has the following attributes:
 * - init: A boolean value indicating whether the annotated method is an initializer method for the saga.
 *          If true, the method will be invoked when a new saga instance is created.
 *          If false (default), the method will be invoked for each matching event that occurs during the saga's lifespan.
 * - associationProperty: A string value indicating the name of the association property used to correlate events with sagas.
 *
 * Example usage:
 *
 *     @SagaEventHandler(init = true, associationProperty = "orderId")
 *     public void handleOrderCreatedEvent(OrderCreatedEvent event) {
 *         // Handle the order created event
 *     }
 *
 *     @SagaEventHandler(associationProperty = "orderId")
 *     public void handleSomeOtherEvent(SomeOtherEvent event) {
 *         // Handle some other event
 *     }
 *
 * Note that the actual implementation of the event handling logic is not shown in the annotation.
 * It should be implemented separately in the annotated method body.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Handler
public @interface SagaEventHandler {
	boolean init() default false;

	String associationProperty();
}
