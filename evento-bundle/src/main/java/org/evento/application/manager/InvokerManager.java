package org.evento.application.manager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.modeling.annotations.component.Invoker;
import org.evento.common.modeling.annotations.handler.InvocationHandler;
import org.evento.common.modeling.bundle.types.ComponentType;
import org.evento.common.modeling.bundle.types.HandlerType;
import org.evento.common.modeling.bundle.types.PayloadType;
import org.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import org.reflections.Reflections;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class InvokerManager {
    private static final Logger logger = LogManager.getLogger(InvokerManager.class);
    private final List<RegisteredHandler> handlers = new ArrayList<>();

    public void parse(Reflections reflections){
        for (Class<?> aClass : reflections.getTypesAnnotatedWith(Invoker.class)) {
            for (Method declaredMethod : aClass.getDeclaredMethods()) {
                if (declaredMethod.getAnnotation(InvocationHandler.class) != null) {
                    var payload = aClass.getSimpleName() + "::" + declaredMethod.getName();
                    handlers.add(new RegisteredHandler(
                            ComponentType.Invoker,
                            aClass.getSimpleName(),
                            HandlerType.InvocationHandler,
                            PayloadType.Invocation,
                            payload,
                            null,
                            false,
                            null
                    ));

                    logger.info("Invoker invocation handler for %s found in %s".formatted(payload, aClass.getName()));

                }
            }
        }
    }

    public List<RegisteredHandler> getHandlers() {
        return handlers;
    }
}
