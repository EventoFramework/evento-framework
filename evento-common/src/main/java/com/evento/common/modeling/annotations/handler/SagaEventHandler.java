package com.evento.common.modeling.annotations.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * SagaEventHandler is an annotation that marks a method as a handler for events in a Saga.
 * <p>
 * It is a meta-annotation with the following elements:
 *  - init: Indicates whether the annotated method is an initialization handler. Default value is false.
 *  - associationProperty: The name of the property used to associate events with a Saga instance.
 * <p>
 * The annotated method should have a single parameter that represents the event being handled.
 * This method will be called whenever an event of the specified type is published.
 * <p>
 * Example usage:
 * <p>
 * \@SagaEventHandler(init=true, associationProperty="orderNumber")
 * public void handleOrderCreatedEvent(OrderCreatedEvent event) {
 *     // Handle order created event
 * }
 * <p>
 * In the above example, the method handleOrderCreatedEvent is marked as an initialization handler,
 * which means it will be called when the Saga is initiated. The association property is "orderNumber".
 * <p>
 * Note:
 * The SagaEventHandler annotation is a meta-annotation, which means it is used to annotate another annotation.
 * It is typically used with the Handler annotation, which is a marker annotation indicating that the annotated
 * annotation is a handler.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Handler
public @interface SagaEventHandler {
	/**
	 * Initializes the annotated method.
	 *
	 * @return true if the method is an initialization handler, false otherwise.
	 */
	boolean init() default false;

	/**
	 * Retrieves the name of the property used to associate events with a Saga instance.
	 *
	 * @return The name of the association property as a {@code String}.
	 */
	String associationProperty();
}
