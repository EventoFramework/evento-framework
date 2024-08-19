package com.evento.application.reference;

import com.evento.application.utils.ReflectionUtils;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.payload.Event;
import com.evento.common.utils.ProjectorStatus;
import com.evento.common.utils.Sleep;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    private static final Logger logger = LogManager.getLogger(ProjectorReference.class);

    private final HashMap<String, Method> eventHandlerReferences = new HashMap<>();

    /**
     * The ProjectorReference class represents a reference to a projector object. It extends the Reference class.
     * It provides methods to register event handlers, retrieve registered events, and invoke event handlers.
     * @param ref the object representing the projector
     */
    public ProjectorReference(Object ref) {
        super(ref);
        for (Method declaredMethod : ref.getClass().getDeclaredMethods()) {

            var ach = declaredMethod.getAnnotation(EventHandler.class);
            if (ach != null) {
                eventHandlerReferences.put(Arrays.stream(declaredMethod.getParameterTypes())
                        .filter(Event.class::isAssignableFrom)
                        .findFirst()
                        .map(Class::getSimpleName)
                        .orElseThrow(() -> new IllegalArgumentException("Event parameter not fount in  " + declaredMethod)), declaredMethod);
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
     * Invokes the event handler for the given published event.
     *
     * @param publishedEvent The published event to process.
     * @param commandGateway The command gateway used for handling command messages.
     * @param queryGateway The query gateway used for handling query messages.
     * @param projectorStatus The status of the projector.
     * @throws Exception If an error occurs while invoking the event handler.
     */
    public void invoke(
            PublishedEvent publishedEvent,
            CommandGateway commandGateway,
            QueryGateway queryGateway,
            ProjectorStatus projectorStatus)
            throws Exception {

        var handler = eventHandlerReferences.get(publishedEvent.getEventName());

        if (handler == null) {
            throw new IllegalArgumentException("No event handler found for event: " + publishedEvent.getEventName());
        }

        var a = handler.getAnnotation(EventHandler.class);
        var retry = 0;
        while (true) {
            try {
                ReflectionUtils.invoke(getRef(), handler,
                        publishedEvent.getEventMessage().getPayload(),
                        commandGateway,
                        queryGateway,
                        publishedEvent.getEventMessage(),
                        publishedEvent.getEventMessage().getMetadata(),
                        projectorStatus,
                        Instant.ofEpochMilli(publishedEvent.getEventMessage().getTimestamp()),
                        publishedEvent.getEventSequenceNumber()
                );
                return;
            }catch (Exception e){
                retry++;
                logger.error("Event processing failed for projector "+ getComponentName() + " event " + publishedEvent.getEventName() + " (attempt " +(retry)+ ")", e);
                if(a.retry() > 0){
                    if (retry > a.retry()) {
                        throw e;
                    }
                }
                Sleep.apply(a.retryDelay());
            }
        }
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
