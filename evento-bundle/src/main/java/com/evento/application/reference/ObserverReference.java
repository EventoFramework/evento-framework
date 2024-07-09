package com.evento.application.reference;

import com.evento.application.utils.ReflectionUtils;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.common.modeling.messaging.message.application.EventMessage;
import com.evento.common.modeling.messaging.payload.Event;
import com.evento.common.utils.Sleep;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

/**
 * ObserverReference is a subclass of Reference and provides functionality for managing event handler references.
 * It allows registering event handlers and invoking them for specific events.
 */
public class ObserverReference extends Reference {
    private static final Logger logger = LogManager.getLogger(ObserverReference.class);

    private final HashMap<String, Method> eventHandlerReferences = new HashMap<>();

    /**
     * Creates a new ObserverReference object.
     *
     * @param ref the reference object to observe
     */
    public ObserverReference(Object ref) {
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
     * Returns a set of registered events for this ObserverReference.
     *
     * @return a set of registered events
     */
    public Set<String> getRegisteredEvents() {
        return eventHandlerReferences.keySet();
    }


    /**
     * Invokes the event handler method for the given EventMessage.
     *
     * @param em the EventMessage to invoke the handler for
     * @param commandGateway the CommandGateway to use for command dispatch
     * @param queryGateway the QueryGateway to use for query dispatch
     * @throws Exception if an error occurs during invocation
     */
    public void invoke(
            EventMessage<? extends Event> em,
            CommandGateway commandGateway,
            QueryGateway queryGateway)
            throws Exception {

        var handler = eventHandlerReferences.get(em.getEventName());

        if (handler == null) {
            throw new IllegalArgumentException("No event handler found for event: " + em.getEventName());
        }

        var a = handler.getAnnotation(EventHandler.class);
        var retry = 0;
        while (true) {
            try {
                ReflectionUtils.invoke(getRef(), handler,
                        em.getPayload(),
                        commandGateway,
                        queryGateway,
                        em,
                        em.getMetadata()
                );
                return;
            }catch (Exception e){
                retry++;
                logger.error("Event processing failed for observer "+ getComponentName() + " event " + em.getEventName() + " (attempt " +(retry)+ ")", e);
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
     * Retrieves the event handler method for the specified event name.
     *
     * @param event the name of the event
     * @return the event handler method for the specified event
     */
    public Method getEventHandler(String event) {
        return eventHandlerReferences.get(event);
    }
}
