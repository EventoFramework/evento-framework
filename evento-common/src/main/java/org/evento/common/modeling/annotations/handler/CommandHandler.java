package org.evento.common.modeling.annotations.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The CommandHandler annotation is used to mark a method as a command handler.
 * Command handlers are methods that handle specific commands in a class.
 * They are discovered and executed by the CommandGateway.
 *
 * @see org.evento.common.modeling.annotations.component.Service
 * @see <a href="https://docs.eventoframework.com/evento-framework/component/aggregate/aggregatecommandhandler">AggregateCommandHandler</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Handler
public @interface CommandHandler {
}
