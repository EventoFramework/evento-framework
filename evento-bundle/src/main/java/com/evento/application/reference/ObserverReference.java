package com.evento.application.reference;

import com.evento.application.manager.MessageHandlerInterceptor;
import com.evento.application.utils.ReflectionUtils;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.payload.Event;
import com.evento.common.utils.Sleep;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
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
     * Invokes the event handler for the given published event.
     *
     * This method handles an event by identifying the appropriate event handler
     * based on the event name. It uses a retry mechanism for handling exceptions
     * during event processing as defined by the {@code EventHandler} annotation's retry configuration.
     * The method also applies message handling interceptors before, after, and during exception scenarios.
     *
     * @param publishedEvent the event to be handled, containing event details such as name and message
     * @param commandGateway the command gateway to be used for handling commands
     * @param queryGateway the query gateway to be used for handling queries
     * @param messageHandlerInterceptor the interceptor for managing message handling behavior
     * @throws Throwable if event processing fails and retry attempts are exhausted
     */
    public void invoke(
            PublishedEvent publishedEvent,
            CommandGateway commandGateway,
            QueryGateway queryGateway, MessageHandlerInterceptor messageHandlerInterceptor)
            throws Throwable {

        var handler = eventHandlerReferences.get(publishedEvent.getEventName());

        if (handler == null) {
            throw new IllegalArgumentException("No event handler found for event: " + publishedEvent.getEventName());
        }

        var a = handler.getAnnotation(EventHandler.class);
        var retry = 0;
        while (true) {
            try {
                messageHandlerInterceptor.beforeObserverEventHandling(
                        getRef(),
                        publishedEvent,
                        commandGateway,
                        queryGateway
                );
                ReflectionUtils.invoke(getRef(), handler,
                        publishedEvent.getEventMessage().getPayload(),
                        commandGateway,
                        queryGateway,
                        publishedEvent.getEventMessage(),
                        publishedEvent,
                        publishedEvent.getEventMessage().getMetadata()
                );
                messageHandlerInterceptor.afterObserverEventHandling(
                        getRef(),
                        publishedEvent,
                        commandGateway,
                        queryGateway
                );
                return;
            }catch (Throwable e){
                retry++;
                logger.error("Event processing failed for observer {} event {} (attempt {})", getComponentName(), publishedEvent.getEventName(), retry, e);
                var throwable = messageHandlerInterceptor.onExceptionObserverEventHandling(
                        getRef(),
                        publishedEvent,
                        commandGateway,
                        queryGateway,
                        e
                );
                if(a.retry() > 0){
                    if (retry > a.retry()) {
                        throw throwable;
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
