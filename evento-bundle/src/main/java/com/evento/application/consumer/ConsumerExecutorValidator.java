package com.evento.application.consumer;

import com.evento.application.reference.ObserverReference;
import com.evento.application.reference.ProjectorReference;
import com.evento.common.messaging.consumer.ConsumerExecutor;
import com.evento.common.modeling.annotations.handler.EventHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Start-up check for {@code @EventHandler(executor = "...")} declarations.
 *
 * <p>A handler naming an executor the bundle does not have would otherwise degrade silently
 * to inline execution: the handler still works, just serially, and the missing parallelism
 * only shows up later as a throughput mystery. Failing the bundle start is the honest
 * outcome.
 */
public final class ConsumerExecutorValidator {

    private static final Logger logger = LogManager.getLogger(ConsumerExecutorValidator.class);

    private ConsumerExecutorValidator() {
    }

    /**
     * @throws IllegalStateException if any handler names an unregistered executor. Logs
     *         warnings for registered-but-unreferenced executors and for {@code retry = -1}
     *         under an executor (coerced to a single attempt).
     */
    public static void validate(Collection<ProjectorReference> projectors,
                                Collection<ObserverReference> observers,
                                Map<String, ConsumerExecutor> registry) {

        Set<String> referenced = new HashSet<>();
        List<String> problems = new ArrayList<>();

        for (var projector : projectors) {
            var component = projector.getRef().getClass().getSimpleName();
            for (var event : projector.getRegisteredEvents()) {
                check(component, projector.getEventHandler(event), registry, referenced, problems);
            }
        }
        for (var observer : observers) {
            var component = observer.getRef().getClass().getSimpleName();
            for (var event : observer.getRegisteredEvents()) {
                check(component, observer.getEventHandler(event), registry, referenced, problems);
            }
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid consumer executor configuration:\n - "
                    + String.join("\n - ", problems)
                    + "\nRegistered executors: " + registry.keySet());
        }
        for (var name : registry.keySet()) {
            if (!referenced.contains(name)) {
                logger.warn("Consumer executor '{}' is registered but no @EventHandler references it", name);
            }
        }
    }

    private static void check(String component,
                              Method method,
                              Map<String, ConsumerExecutor> registry,
                              Set<String> referenced,
                              List<String> problems) {
        if (method == null) return;
        var annotation = method.getAnnotation(EventHandler.class);
        if (annotation == null || annotation.executor().isBlank()) return;

        var name = annotation.executor();
        referenced.add(name);
        if (!registry.containsKey(name)) {
            problems.add("%s.%s references consumer executor '%s', which is not registered"
                    .formatted(component, method.getName(), name));
            return;
        }
        if (annotation.retry() < 0) {
            logger.warn("{}.{} runs on executor '{}' with retry=-1 (retry forever); coerced to a "
                            + "single attempt so it cannot pin a concurrency permit. Set an explicit "
                            + "retry if this handler calls a remote dependency.",
                    component, method.getName(), name);
        }
    }
}
