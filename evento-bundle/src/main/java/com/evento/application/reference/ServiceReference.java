package com.evento.application.reference;


import com.evento.application.manager.MessageHandlerInterceptor;
import com.evento.application.utils.ReflectionUtils;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.evento.common.modeling.messaging.message.application.CommandMessage;
import com.evento.common.modeling.messaging.payload.ServiceCommand;
import com.evento.common.modeling.messaging.payload.ServiceEvent;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

/**
 * The ServiceReference class represents a reference to a service object. It extends the Reference class.
 */
public class ServiceReference extends Reference {


    private final HashMap<String, Method> serviceCommandHandlerReferences = new HashMap<>();

    /**
     * The ServiceReference class represents a reference to a service object. It extends the Reference class.
     * @param ref The reference to the service object
     */
    public ServiceReference(Object ref) {
        super(ref);
        for (Method declaredMethod : ref.getClass().getDeclaredMethods()) {

            var ach = declaredMethod.getAnnotation(CommandHandler.class);
            if (ach != null) {
                serviceCommandHandlerReferences.put(Arrays.stream(declaredMethod.getParameterTypes())
                        .filter(ServiceCommand.class::isAssignableFrom)
                        .findFirst()
                        .map(Class::getSimpleName)
                        .orElseThrow(() -> new IllegalArgumentException("ServiceCommand parameter not fount in  " + declaredMethod)), declaredMethod);
            }
        }
    }


    /**
     * Retrieves the aggregate command handler method associated with the specified event name.
     *
     * @param eventName The name of the event.
     * @return The aggregate command handler method associated with the event name, or null if not found.
     */
    public Method getAggregateCommandHandler(String eventName) {
        return serviceCommandHandlerReferences.get(eventName);
    }

    /**
     * Retrieves the set of registered command names.
     *
     * @return A set containing the names of the registered commands.
     */
    public Set<String> getRegisteredCommands() {
        return serviceCommandHandlerReferences.keySet();
    }

    /**
     * Invokes a service command handler based on the provided {@link CommandMessage}.
     * The method processes a service command, executes the appropriate handler method,
     * and manages pre- and post-process actions via the {@link MessageHandlerInterceptor}.
     *
     * @param cm The command message containing the payload, metadata, and other command-related details.
     * @param commandGateway The gateway for dispatching commands during command handling.
     * @param queryGateway The gateway for sending queries during command handling.
     * @param messageHandlerInterceptor The interceptor for managing pre- and post-command processing.
     * @return The {@link ServiceEvent} generated as the result of the command handling.
     * @throws Throwable If any exception occurs during command handling or interception, the method throws it.
     */
    public ServiceEvent invoke(
            CommandMessage<? extends ServiceCommand> cm,
            CommandGateway commandGateway,
            QueryGateway queryGateway, MessageHandlerInterceptor messageHandlerInterceptor)
            throws Throwable {

        var commandHandler = serviceCommandHandlerReferences.get(cm.getCommandName());

        try {
            messageHandlerInterceptor.beforeServiceCommandHandling(
                    getRef(),
                    cm,
                    commandGateway,
                    queryGateway
            );

            var resp = (ServiceEvent) ReflectionUtils.invoke(getRef(), commandHandler,
                    cm.getPayload(),
                    commandGateway,
                    queryGateway,
                    cm,
                    cm.getMetadata()
            );

            return messageHandlerInterceptor.afterServiceCommandHandling(
                    getRef(),
                    cm,
                    commandGateway,
                    queryGateway,
                    resp
            );
        }catch (Throwable t){
            throw messageHandlerInterceptor.onExceptionServiceCommandHandling(
                    getRef(),
                    cm,
                    commandGateway,
                    queryGateway,
                    t
            );

        }
    }
}
