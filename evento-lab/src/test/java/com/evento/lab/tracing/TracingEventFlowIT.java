package com.evento.lab.tracing;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.lab.api.command.CreateOrderCommand;
import com.evento.lab.bundle.LabStore;
import com.evento.lab.support.CommandAwareEmbeddedBroker;
import com.evento.opentelemetry.OpenTelemetryTracingAgent;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * One trace from an HTTP request to the read model, over real TCP: under the request span the
 * bundle's gateway sends a command in a CLIENT span, the broker executes it in a child span (lock, story,
 * append as attributes) and forwards it to the aggregate as that span's child, stores the event
 * with the metadata the aggregate's span put in it, and the projector consuming that event from
 * the store continues the same trace.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class TracingEventFlowIT {

    private final InMemorySpanExporter spans = InMemorySpanExporter.create();
    private OpenTelemetrySdk sdk;
    private CommandAwareEmbeddedBroker broker;
    private EventoBundle bundle;

    @BeforeEach
    void setUp() throws Exception {
        LabStore.reset();
        sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spans)).build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        broker = new CommandAwareEmbeddedBroker();
        broker.commandBroker().setOpenTelemetry(sdk);
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(LabStore.class.getPackage())
                .setBundleId("lab-bundle")
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .setTracingAgent(new OpenTelemetryTracingAgent("lab-bundle", 1, sdk))
                .start();
        await().atMost(20, TimeUnit.SECONDS).until(() -> broker.lifecycle().isBundleAvailable("lab-bundle"));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (bundle != null) bundle.stop(Duration.ofSeconds(5));
        if (broker != null) broker.close();
        sdk.close();
    }

    @Test
    void theProjectorOfTheEventContinuesTheTraceOfTheRequest() throws Exception {
        var orderId = "trace-" + UUID.randomUUID();
        var request = sdk.getTracer("test").spanBuilder("POST /orders").setSpanKind(SpanKind.SERVER).startSpan();
        try (var ignored = request.makeCurrent()) {
            bundle.getCommandGateway().send(new CreateOrderCommand(orderId, "traced", 1)).get(30, TimeUnit.SECONDS);
        } finally {
            request.end();
        }
        await().atMost(15, TimeUnit.SECONDS).until(() -> LabStore.get(orderId) != null);
        await().atMost(5, TimeUnit.SECONDS).until(() -> span("LabProjector").isPresent());

        var traceId = request.getSpanContext().getTraceId();
        var send = span("send").orElseThrow();
        var server = span("evento command").orElseThrow();
        var aggregate = span("LabAggregate").orElseThrow();
        var projector = span("LabProjector").orElseThrow();

        assertThat(send.getTraceId()).isEqualTo(traceId);
        assertThat(send.getParentSpanId()).isEqualTo(request.getSpanContext().getSpanId());
        assertThat(send.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(server.getTraceId()).isEqualTo(traceId);
        assertThat(server.getParentSpanId()).isEqualTo(send.getSpanId());
        assertThat(server.getAttributes().asMap().keySet()).anyMatch(k -> k.getKey().equals("evento.phase.story.ms"))
                .anyMatch(k -> k.getKey().equals("evento.phase.append.ms"));
        assertThat(aggregate.getTraceId()).isEqualTo(traceId);
        assertThat(aggregate.getParentSpanId()).isEqualTo(server.getSpanId());
        assertThat(aggregate.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(projector.getTraceId()).isEqualTo(traceId);
        assertThat(projector.getParentSpanId()).isEqualTo(aggregate.getSpanId());
        assertThat(projector.getKind()).isEqualTo(SpanKind.CONSUMER);
    }

    private Optional<SpanData> span(String component) {
        return spans.getFinishedSpanItems().stream()
                .filter(s -> s.getName().startsWith(component + " "))
                .filter(s -> s.getName().contains("CreateOrderCommand") || s.getName().contains("OrderCreatedEvent"))
                .findFirst();
    }
}
