package com.evento.common.modeling.annotations.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Invoker annotation serves as a marker for classes that act as invokers in the RECQ component pattern.
 * Invokers are responsible for invoking operations or methods in other components within a software system.
 * This annotation is used in conjunction with other annotations such as {@link Service}, {@link Projection},
 * {@link Saga}, {@link Observer}, {@link Projector}, and {@link Aggregate} to categorize and identify various
 * roles or characteristics of classes.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {@code
 * @Invoker
 * public class MyInvoker {
 *
 *     @InvocationHandler
 *     public void invokeOperation() {
 *         // Invoke the operation on the service
 *     }
 * }
 * }
 * </pre>
 *
 * @see Service
 * @see Projection
 * @see Saga
 * @see Observer
 * @see Projector
 * @see Aggregate
 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern/invoker">Invoker in RECQ Component Pattern</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface Invoker {
}
