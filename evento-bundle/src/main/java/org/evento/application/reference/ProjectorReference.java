package org.evento.application.reference;

import org.evento.application.utils.ReflectionUtils;
import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.messaging.gateway.QueryGateway;
import org.evento.common.modeling.annotations.handler.EventHandler;
import org.evento.common.modeling.messaging.message.application.EventMessage;
import org.evento.common.modeling.messaging.payload.Event;
import org.evento.common.utils.ProjectorStatus;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

/**
 * The ProjectorReference class represents a reference to a projector object. It extends the Reference class.
 * It provides methods to register event handlers, retrieve registered events, and invoke event handlers.
 */
public class ProjectorReference extends Reference {

    private final HashMap<String, Method> eventHandlerReferences = new HashMap<>();

    /**
     * The ProjectorReference class represents a reference to a projector object. It extends the Reference class.
     * It provides methods to register event handlers, retrieve registered events, and invoke event handlers.
     */
    public ProjectorReference(Object ref) {
        super(ref);
        for (Method declaredMethod : ref.getClass().getDeclaredMethods()) {

            var ach = declaredMethod.getAnnotation(EventHandler.class);
            if (ach != null) {
                eventHandlerReferences.put(Arrays.stream(declaredMethod.getParameterTypes())
                        .filter(Event.class::isAssignableFrom)
                        .findFirst()
                        .map(Class::getSimpleName).orElseThrow(), declaredMethod);
            }
        }
    }

    /**
     * Retrieves the set of registered events for the projector.
     *
     * @return the set of registered events
     */
    public Set<String> getRegisteredEvents() {
        return eventHandlerReferences.keySet();
    }


    /**
     * Invokes an event handler for a given event message.
     *
     * @param em             The event message containing the event and its metadata.
     * @param commandGateway The command gateway.
     * @param queryGateway   The query gateway.
     * @param projectorStatus The status of the projector.
     * @throws Exception If an error occurs while invoking the event handler.
     */
    public void invoke(
            EventMessage<? extends Event> em,
            CommandGateway commandGateway,
            QueryGateway queryGateway,
            ProjectorStatus projectorStatus)
            throws Exception {

        var handler = eventHandlerReferences.get(em.getEventName());

        ReflectionUtils.invoke(getRef(), handler,
                em.getPayload(),
                commandGateway,
                queryGateway,
                em,
                em.getMetadata(),
                projectorStatus,
                Instant.ofEpochMilli(em.getTimestamp())
        );
    }

    /**
     * Retrieves the event handler method for a given event.
     *
     * @param event The name of the event.
     * @return The event handler method for the specified event.
     */
    public Method getEventHandler(String event) {
        return eventHandlerReferences.get(event);
    }
}
