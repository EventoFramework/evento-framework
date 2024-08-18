package com.evento.application.reference;


import com.evento.application.utils.ReflectionUtils;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.evento.common.modeling.messaging.message.application.EventMessage;
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
     * Invokes the saga event handler method for the given event and returns the updated saga state.
     *
     * @param em            the event message containing the event
     * @param sagaState     the current saga state
     * @param commandGateway the command gateway for sending commands
     * @param queryGateway  the query gateway for querying data
     * @return the updated saga state
     * @throws Exception if an error occurs during the invocation
     */
    public SagaState invoke(
            EventMessage<? extends Event> em,
            SagaState sagaState,
            CommandGateway commandGateway,
            QueryGateway queryGateway)
            throws Exception {

        var handler = sagaEventHandlerReferences.get(em.getEventName());

        var a = handler.getAnnotation(SagaEventHandler.class);
        var retry = 0;
        while (true) {
            try {
                var state = (SagaState) ReflectionUtils.invoke(getRef(), handler,
                        em.getPayload(),
                        sagaState,
                        commandGateway,
                        queryGateway,
                        em,
                        em.getMetadata(),
                        Instant.ofEpochMilli(em.getTimestamp())
                );
                if (state == null) {
                    return sagaState;
                } else {
                    return state;
                }
            }catch (Exception e){
                retry++;
                logger.error("Event processing failed for saga "+ getComponentName() + " event " + em.getEventName() + " (attempt " +(retry)+ ")", e);
                if(a.retry() > 0){
                    if (retry > a.retry()) {
                        throw e;
                    }
                }
                Sleep.apply(a.retryDelay());
            }
        }


    }
}
