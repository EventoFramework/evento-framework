package com.evento.application.consumer;

import com.evento.application.reference.ObserverReference;
import com.evento.application.reference.ProjectorReference;
import com.evento.common.messaging.consumer.ConsumerExecutor;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.common.modeling.messaging.dto.PublishedEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Builds the {@code event → ConsumerExecutor} function an engine hands to
 * {@code ConsumerProcessor}.
 *
 * <p>The mapping is static — it comes from {@link EventHandler#executor()} on the handler
 * method — so it is resolved once at engine construction and served from a map afterwards,
 * rather than reflecting on every event.
 *
 * <p>A {@code null} result means "run inline", which is both the default and the behaviour
 * of every handler that does not name an executor.
 */
public final class ConsumerExecutorResolver {

    private ConsumerExecutorResolver() {
    }

    /**
     * How one consumer routes its events.
     *
     * @param resolver      event → executor, or {@code null} for the inline path
     * @param executorNames every executor this consumer can dispatch to (for the dashboard)
     */
    public record Routing(Function<PublishedEvent, ConsumerExecutor> resolver,
                          Set<String> executorNames) {

        /** Everything inline — the shape for a consumer with no async handlers. */
        public static final Routing INLINE = new Routing(event -> null, Set.of());

        public ConsumerExecutor resolve(PublishedEvent event) {
            return resolver.apply(event);
        }

        public boolean isFullyInline() {
            return executorNames.isEmpty();
        }
    }

    /**
     * @param handlers  the engine's {@code eventName → componentName → reference} map
     * @param projectorName the component this engine serves
     * @param registry  executors registered on the bundle, by name
     */
    public static Routing forProjector(
            Map<String, HashMap<String, ProjectorReference>> handlers,
            String projectorName,
            Map<String, ConsumerExecutor> registry) {
        return build(handlers, projectorName, registry, ProjectorReference::getEventHandler);
    }

    /** @see #forProjector */
    public static Routing forObserver(
            Map<String, HashMap<String, ObserverReference>> handlers,
            String observerName,
            Map<String, ConsumerExecutor> registry) {
        return build(handlers, observerName, registry, ObserverReference::getEventHandler);
    }

    private static <R> Routing build(
            Map<String, HashMap<String, R>> handlers,
            String componentName,
            Map<String, ConsumerExecutor> registry,
            java.util.function.BiFunction<R, String, Method> handlerLookup) {

        if (registry.isEmpty()) {
            // No executors configured — nothing can be async, so skip the map entirely.
            return Routing.INLINE;
        }

        var byEvent = new HashMap<String, ConsumerExecutor>();
        var names = new java.util.LinkedHashSet<String>();
        for (var entry : handlers.entrySet()) {
            var eventName = entry.getKey();
            var reference = entry.getValue().get(componentName);
            if (reference == null) continue;
            var executorName = executorNameOf(handlerLookup.apply(reference, eventName));
            if (executorName == null) continue;
            var executor = registry.get(executorName);
            // Absent names are rejected at start-up (see ConsumerExecutorValidator);
            // tolerate here so a routing built in a test without validation degrades to
            // inline rather than throwing.
            if (executor != null) {
                byEvent.put(eventName, executor);
                names.add(executorName);
            }
        }

        if (byEvent.isEmpty()) return Routing.INLINE;
        return new Routing(event -> byEvent.get(event.getEventName()), Set.copyOf(names));
    }

    /** @return the declared executor name, or {@code null} when the handler runs inline. */
    static String executorNameOf(Method handler) {
        if (handler == null) return null;
        var annotation = handler.getAnnotation(EventHandler.class);
        if (annotation == null || annotation.executor().isBlank()) return null;
        return annotation.executor();
    }
}
