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
    int retry() default -1;
    /**
     * The retryDelay method is used to specify the delay in milliseconds between each retry attempt
     * when executing a specific action.
     * This method is an annotation attribute, typically used in conjunction with the EventHandler annotation.
     *
     * @return the delay in milliseconds between each retry attempt. The default value is 1000,
     * indicating no specific retry delay.
     */
    int retryDelay() default 1000;

    /**
     * Name of a {@link com.evento.common.messaging.consumer.ConsumerExecutor} registered on
     * the bundle builder. When set, the consumer dispatches this event to that executor
     * instead of running it inline, so events are handled <b>in parallel</b> up to the
     * executor's capacity.
     *
     * <p>Empty (the default) keeps the handler on the sequential path: the consumer waits
     * for it to return before moving to the next event.
     *
     * <h2>Only for idempotent or overwrite handlers</h2>
     * <p>Parallel dispatch gives up two guarantees:
     * <ul>
     *   <li><b>Order.</b> Two events, including two events on the same aggregate, may be
     *       applied out of sequence order. Only use this where the handler's effect is
     *       idempotent or a blind overwrite (upsert a row, set a cache key).</li>
     *   <li><b>At-least-once delivery for running tasks.</b> The consumer checkpoint
     *       advances when a task <em>starts</em>, which is what bounds how far ahead of
     *       completion the consumer may run. If the JVM dies abruptly, events whose handler
     *       was mid-flight are not redelivered. A graceful shutdown drains them; a
     *       {@code kill -9} does not.</li>
     * </ul>
     *
     * <p>Unknown executor names fail bundle start-up. Not available on
     * {@link SagaEventHandler}: saga handlers are a read-modify-write on shared saga state,
     * which parallel dispatch would corrupt.
     *
     * <p><b>{@link #retry()} behaves differently here.</b> The default {@code -1}
     * (retry forever) would pin a concurrency permit indefinitely and starve the executor,
     * so under an executor it is coerced to {@code 0}: one attempt, then the dead-event
     * queue. Set an explicit {@code retry} for any async handler that touches a remote
     * dependency, otherwise a transient blip dead-letters the event on first failure.
     *
     * @return the executor name, or {@code ""} to run inline. The default is {@code ""}.
     */
    String executor() default "";
}
