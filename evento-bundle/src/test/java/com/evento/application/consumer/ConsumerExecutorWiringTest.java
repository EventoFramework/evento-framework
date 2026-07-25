package com.evento.application.consumer;

import com.evento.application.reference.ProjectorReference;
import com.evento.common.messaging.consumer.ConsumerExecutor;
import com.evento.common.messaging.consumer.ConsumerExecutors;
import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.payload.Event;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Start-up validation and event → executor resolution for
 * {@code @EventHandler(executor = "...")}.
 */
class ConsumerExecutorWiringTest {

    // --- Fixtures -----------------------------------------------------------

    public static class AsyncEvent extends Event {}
    public static class InlineEvent extends Event {}
    public static class ForeverRetryEvent extends Event {}

    @Projector(version = 1)
    public static class MixedProjector {
        @EventHandler(executor = "read-model", retry = 3)
        public void on(AsyncEvent e) {}

        @EventHandler
        public void on(InlineEvent e) {}
    }

    @Projector(version = 1)
    public static class TypoProjector {
        @EventHandler(executor = "raed-model")
        public void on(AsyncEvent e) {}
    }

    @Projector(version = 1)
    public static class ForeverRetryProjector {
        @EventHandler(executor = "read-model")   // retry defaults to -1
        public void on(ForeverRetryEvent e) {}
    }

    // --- Validation ---------------------------------------------------------

    @Test
    void unknownExecutorNameFailsStartUp() {
        Map<String, ConsumerExecutor> registry =
                Map.of("read-model", ConsumerExecutors.virtual("read-model", 2));

        assertThatThrownBy(() -> ConsumerExecutorValidator.validate(
                List.of(new ProjectorReference(new TypoProjector())), List.of(), registry))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("raed-model")
                .hasMessageContaining("TypoProjector")
                // The message must show what IS available, or the typo is still a guess.
                .hasMessageContaining("read-model");
    }

    @Test
    void registeredExecutorsAndPlainHandlersValidate() {
        Map<String, ConsumerExecutor> registry =
                Map.of("read-model", ConsumerExecutors.virtual("read-model", 2));

        assertThatCode(() -> ConsumerExecutorValidator.validate(
                List.of(new ProjectorReference(new MixedProjector())), List.of(), registry))
                .doesNotThrowAnyException();
    }

    @Test
    void retryForeverUnderAnExecutorIsAllowedAndCoercedNotRejected() {
        Map<String, ConsumerExecutor> registry =
                Map.of("read-model", ConsumerExecutors.virtual("read-model", 2));

        // It warns rather than failing; the coercion to a single attempt happens in
        // ProjectorReference.invoke.
        assertThatCode(() -> ConsumerExecutorValidator.validate(
                List.of(new ProjectorReference(new ForeverRetryProjector())), List.of(), registry))
                .doesNotThrowAnyException();
    }

    @Test
    void noExecutorsConfiguredAndNoneReferencedIsValid() {
        assertThatCode(() -> ConsumerExecutorValidator.validate(
                List.of(new ProjectorReference(new MixedProjector() {
                    @EventHandler
                    public void on(InlineEvent e) {}
                })), List.of(), Map.of()))
                .doesNotThrowAnyException();
    }

    // --- Resolution ---------------------------------------------------------

    @Test
    void resolverRoutesOnlyHandlersThatNameAnExecutor() {
        var executor = ConsumerExecutors.virtual("read-model", 2);
        var reference = new ProjectorReference(new MixedProjector());

        var handlers = new HashMap<String, HashMap<String, ProjectorReference>>();
        for (var event : reference.getRegisteredEvents()) {
            handlers.computeIfAbsent(event, k -> new HashMap<>()).put("MixedProjector", reference);
        }

        var routing = ConsumerExecutorResolver.forProjector(
                handlers, "MixedProjector", Map.of("read-model", executor));

        assertThat(routing.resolve(event("AsyncEvent"))).isSameAs(executor);
        assertThat(routing.resolve(event("InlineEvent"))).isNull();
        assertThat(routing.resolve(event("UnknownEvent"))).isNull();
        // Reported on the consumer status so the dashboard can show which executors a
        // consumer depends on.
        assertThat(routing.executorNames()).containsExactly("read-model");
        assertThat(routing.isFullyInline()).isFalse();
    }

    @Test
    void emptyRegistryResolvesEverythingInline() {
        var reference = new ProjectorReference(new MixedProjector());
        var handlers = new HashMap<String, HashMap<String, ProjectorReference>>();
        for (var event : reference.getRegisteredEvents()) {
            handlers.computeIfAbsent(event, k -> new HashMap<>()).put("MixedProjector", reference);
        }

        var routing = ConsumerExecutorResolver.forProjector(handlers, "MixedProjector", Map.of());

        assertThat(routing.resolve(event("AsyncEvent"))).isNull();
        assertThat(routing.isFullyInline()).isTrue();
    }

    @Test
    void resolverIgnoresOtherComponentsHandlersForTheSameEvent() {
        var executor = ConsumerExecutors.virtual("read-model", 2);
        var reference = new ProjectorReference(new MixedProjector());

        var handlers = new HashMap<String, HashMap<String, ProjectorReference>>();
        for (var event : reference.getRegisteredEvents()) {
            handlers.computeIfAbsent(event, k -> new HashMap<>()).put("MixedProjector", reference);
        }

        var routing = ConsumerExecutorResolver.forProjector(
                handlers, "SomeOtherProjector", Map.of("read-model", executor));

        assertThat(routing.resolve(event("AsyncEvent"))).isNull();
        assertThat(routing.isFullyInline()).isTrue();
    }

    private static PublishedEvent event(String name) {
        var e = new PublishedEvent();
        e.setEventName(name);
        return e;
    }
}
