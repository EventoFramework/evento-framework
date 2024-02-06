package com.evento.common.modeling.annotations.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation serves as a marker for classes that are considered RECQ services.
 * RECQ services are used to perform specific tasks or operations within a software system.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {@code
 * @Service
 * public class MyService {
 *
 *     @CommandHandler
 *     public void performOperation(OperationServiceCommand command) {
 *         // Perform the operation logic
 *     }
 * }
 * }
 * </pre>
 *
 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern/service">Service in RECQ Component Patterns</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface Service {
}
