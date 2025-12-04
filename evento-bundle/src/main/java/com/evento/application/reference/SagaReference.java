package com.evento.application.reference;


import com.evento.application.manager.ConsumerSetError;
import com.evento.application.manager.MessageHandlerInterceptor;
import com.evento.application.utils.ReflectionUtils;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.payload.Event;
import com.evento.common.modeling.state.SagaState;
import com.evento.common.utils.Sleep;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

/**
 * The SagaReference class is a subclass of Reference that represents a reference to a Saga object.
 * It provides functionality for managing saga event handler references, invoking saga methods, and retrieving registered events.
 */
public class SagaReference extends Reference {

    private static final Logger logger = LogManager.getLogger(SagaReference.class);

    private final HashMap<String, Method> sagaEventHandlerReferences = new HashMap<>();

    /**
     * The SagaReference class is a subclass of Reference that represents a reference to a Saga object.
     * It provides functionality for managing saga event handler references, invoking saga methods, and retrieving registered events.
     * @param ref The saga object
     */
    public SagaReference(Object ref) {
        super(ref);
        for (Method declaredMethod : ref.getClass().getDeclaredMethods()) {

            var ach = declaredMethod.getAnnotation(SagaEventHandler.class);
            if (ach != null) {
                sagaEventHandlerReferences.put(Arrays.stream(declaredMethod.getParameterTypes())
                        .filter(Event.class::isAssignableFrom)
                        .findFirst()
                        .map(Class::getSimpleName)
                        .orElseThrow(() -> new IllegalArgumentException("Event parameter not fount in  " + declaredMethod)), declaredMethod);
            }
        }
    }


    /**
     * Retrieves the saga event handler method for the given event name.
     *
     * @param eventName the name of the event
     * @return the saga event handler method
     */
    public Method getSagaEventHandler(String eventName) {
        return sagaEventHandlerReferences.get(eventName);
    }

    /**
     * Retrieves the set of registered events for the SagaReference.
     *
     * @return the set of registered events
     */
    public Set<String> getRegisteredEvents() {
        return sagaEventHandlerReferences.keySet();
    }

    /**
     * Invokes a saga event handler based on the provided published event, current saga state,
     * and gateway interfaces, while managing retries and invoking the appropriate interceptors.
     *
     * @param publishedEvent the published event containing the necessary event information
     * @param sagaState the current state of the saga
     * @param commandGateway the gateway for sending commands within the saga
     * @param queryGateway the gateway for performing queries within the saga
     * @param messageHandlerInterceptor the interceptor for handling pre- and post-processing logic
     * @param setError the error setter
     * @return the updated saga state after the event handler execution
     * @throws Throwable if an exception occurs during the event handling process
     */
    public SagaState invoke(
            PublishedEvent publishedEvent,
            SagaState sagaState,
            CommandGateway commandGateway,
            QueryGateway queryGateway, MessageHandlerInterceptor messageHandlerInterceptor,
            ConsumerSetError setError)
            throws Throwable {

        var handler = sagaEventHandlerReferences.get(publishedEvent.getEventName());

        var a = handler.getAnnotation(SagaEventHandler.class);
        var retry = 0;
        while (true) {
            try {
                messageHandlerInterceptor.beforeSagaEventHandling(
                        getRef(),
                        publishedEvent,
                        commandGateway,
                        queryGateway,
                        sagaState

                );
                var state = (SagaState) ReflectionUtils.invoke(getRef(), handler,
                        publishedEvent.getEventMessage().getPayload(),
                        sagaState,
                        commandGateway,
                        queryGateway,
                        publishedEvent,
                        publishedEvent.getEventMessage().getMetadata(),
                        Instant.ofEpochMilli(publishedEvent.getEventMessage().getTimestamp())
                );

                return messageHandlerInterceptor.afterSagaEventHandling(
                        getRef(),
                        publishedEvent,
                        commandGateway,
                        queryGateway,
                        state == null ? sagaState : state
                );
            }catch (Throwable e){

                Throwable throwable = e;
                retry++;
                logger.error("Event processing failed for saga {} event {} (attempt {})", getComponentName(), publishedEvent.getEventName(), retry, e);
                try {
                    throwable = messageHandlerInterceptor.onExceptionSagaEventHandling(
                            getRef(),
                            publishedEvent,
                            commandGateway,
                            queryGateway,
                            sagaState,
                            e
                    );
                }catch (Exception ex){
                    logger.error("Exception while handling exception for saga {} event {} (attempt {})", getComponentName(), publishedEvent.getEventName(), retry, ex);
                    throwable = ex;
                }
                setError.setError(throwable);
                if(a.retry() >= 0){
                    if (retry > a.retry()) {
                        throw throwable;
                    }
                }
                Sleep.apply(a.retryDelay());
            }
        }


    }
}
