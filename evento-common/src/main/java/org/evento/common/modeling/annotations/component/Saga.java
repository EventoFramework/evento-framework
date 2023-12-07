package org.evento.common.modeling.annotations.component;

import org.evento.common.utils.Context;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to mark a class as a Saga.
 * Sagas are long-running processes that coordinate
 * interactions between multiple components to achieve a specific goal.
 *
 * <p>
 * Example usage:
 * </p>
 * <pre>
 * {@code
 * @Saga(version = 1, context = {Context.DEFAULT})
 * public class DemoSaga {
 *
 *     private final DemoRepository demoRepository;
 *     private final CommandGateway commandGateway;
 *
 *     public DemoSaga(DemoRepository demoRepository, CommandGateway commandGateway) {
 *         this.demoRepository = demoRepository;
 *         this.commandGateway = commandGateway;
 *     }
 *
 *     @StartsSaga
 *     @EventHandler
 *     public void on(DemoCreatedEvent event) {
 *         // Handle event logic
 *     }
 *
 *     @EventHandler
 *     public void on(DemoUpdatedEvent event) {
 *         // Handle event logic
 *     }
 *
 *     @EndsSaga
 *     @EventHandler
 *     public void on(DemoDeletedEvent event) {
 *         // Handle event logic
 *     }
 * }
 * }
 * </pre>
 *
 * @see Service
 * @see Projection
 * @see Invoker
 * @see Projector
 * @see Observer
 * @see SagaEventHandler
 * @see StartsSaga
 * @see EndsSaga
 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern/saga">Saga in RECQ Component Patterns</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface Saga {
	int version();

	String[] context() default  {Context.ALL};
}
