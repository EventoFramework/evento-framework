package com.evento.common.modeling.annotations.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Saga annotation is a marker annotation used to designate a class as a Saga within a software system.
 * Sagas are long-running, distributed transactions that coordinate actions across multiple components.
 * This annotation is used in conjunction with the Component annotation to categorize and identify the class as a Saga.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {@code
 * @Saga(version = 1, context = {Context.DEFAULT})
 * public class OrderSaga {
 *
 *     private final OrderService orderService;
 *     private final PaymentService paymentService;
 *
 *     public OrderSaga(OrderService orderService, PaymentService paymentService) {
 *         this.orderService = orderService;
 *         this.paymentService = paymentService;
 *     }
 *
 *     @EventHandler
 *     public void on(OrderCreatedEvent event) {
 *         // Handle event logic
 *     }
 *
 *     @EventHandler
 *     public void on(PaymentCompletedEvent event) {
 *         // Handle event logic
 *     }
 * }
 * }
 * </pre>
 *
 * @see com.evento.common.modeling.annotations.handler.EventHandler
 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern/saga">Saga in RECQ Component Pattern</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface Saga {
	/**
	 * Returns the version of the method.
	 *
	 * @return the version of the method
	 */
	int version();
}
