package org.evento.application.manager;

import lombok.Getter;
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

/**
 * The InvokerManager class is responsible for parsing the classes and methods annotated with specific annotations
 * to find invoker invocation handlers. It logs the found handlers and provides access to the registered handlers.
 */
@Getter
public class InvokerManager {
    private static final Logger logger = LogManager.getLogger(InvokerManager.class);

    private final List<RegisteredHandler> handlers = new ArrayList<>();

    /**
     * Parses the given Reflections object to find class methods annotated with @Invoker and @InvocationHandler.
     * Adds the found handlers to the handlers list.
     *
     * @param reflections the Reflections object to parse
     */
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

}
