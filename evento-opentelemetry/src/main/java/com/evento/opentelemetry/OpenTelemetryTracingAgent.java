package com.evento.opentelemetry;

import com.evento.application.performance.TracingAgent;
import com.evento.application.performance.Track;
import com.evento.common.modeling.messaging.message.application.EventMessage;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;

import java.util.List;

/**
 * A {@link TracingAgent} that traces with OpenTelemetry. Opt-in: a bundle without it runs the
 * no-op base agent and pays nothing.
 *
 * <pre>{@code
 * EventoBundle.Builder.builder()
 *     .setTracingAgent(new OpenTelemetryTracingAgent("orders", 1, GlobalOpenTelemetry.get()))
 *     ...
 * }</pre>
 *
 * <ul>
 *   <li><b>A span per handler</b>: every command, query, event and invocation handler runs in a
 *       span named {@code <component> <payload>} ({@code SERVER} for commands, queries and
 *       invocations, {@code CONSUMER} for events), with the error recorded when it throws.</li>
 *   <li><b>Trace context in the message metadata</b>: whatever a handler sends (commands, queries,
 *       the event a command produces) carries the W3C {@code traceparent}/{@code tracestate} of
 *       its span, through the server, which does not read it. So does what application code sends
 *       through {@code EventoBundle.getCommandGateway()}/{@code getQueryGateway()} while a span is
 *       current, e.g. inside an instrumented HTTP request.</li>
 *   <li><b>Consumers</b>: by default a projector, saga or observer span is a child of the span
 *       that produced the event, so a trace runs from the HTTP request to the read models. With
 *       {@link #linkingConsumers()} it starts a new trace with a link instead: better when events
 *       are replayed or consumed long after they were stored.</li>
 * </ul>
 *
 * <p>Only the OpenTelemetry API is a dependency: the application configures the SDK (or runs the
 * Java agent) and decides where spans go. With no SDK the API is a no-op as well.
 */
public class OpenTelemetryTracingAgent extends TracingAgent {

    /** The instrumentation scope name. */
    public static final String SCOPE = "com.evento";

    private static final List<String> TRACE_KEYS = List.of("traceparent", "tracestate");

    private static final TextMapGetter<Metadata> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Metadata carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Metadata carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final TextMapPropagator propagator;
    private final boolean linkConsumers;

    public OpenTelemetryTracingAgent(String bundleId, long bundleVersion, OpenTelemetry openTelemetry) {
        this(bundleId, bundleVersion, openTelemetry, false);
    }

    private OpenTelemetryTracingAgent(String bundleId, long bundleVersion, OpenTelemetry openTelemetry,
                                      boolean linkConsumers) {
        super(bundleId, bundleVersion);
        this.tracer = openTelemetry.getTracer(SCOPE, String.valueOf(bundleVersion));
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
        this.linkConsumers = linkConsumers;
        this.openTelemetry = openTelemetry;
    }

    /** This agent, but event consumers start a new trace linked to the producer instead of joining it. */
    public OpenTelemetryTracingAgent linkingConsumers() {
        return new OpenTelemetryTracingAgent(getBundleId(), getBundleVersion(), openTelemetry, true);
    }

    @Override
    protected <T> T doTrack(Message<?> message, String component, Track trackingAnnotation,
                            Transaction<T> transaction) throws Throwable {
        var event = message instanceof EventMessage<?>;
        var producer = propagator.extract(Context.root(), message.getMetadata(), GETTER);
        var payload = message.getPayloadName();
        var builder = tracer.spanBuilder(component + " " + payload)
                .setSpanKind(event ? SpanKind.CONSUMER : SpanKind.SERVER)
                .setAttribute("messaging.system", "evento")
                .setAttribute("evento.bundle", getBundleId())
                .setAttribute("evento.component", component)
                .setAttribute("evento.payload", payload == null ? "" : payload);
        if (event && linkConsumers) {
            builder.setNoParent();
            var producerSpan = Span.fromContext(producer).getSpanContext();
            if (producerSpan.isValid()) builder.addLink(producerSpan);
        } else {
            builder.setParent(Span.fromContext(producer).getSpanContext().isValid() ? producer : Context.current());
        }
        var span = builder.startSpan();
        try (var ignored = span.makeCurrent()) {
            return transaction.run();
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getClass().getSimpleName());
            throw t;
        } finally {
            span.end();
        }
    }

    /**
     * The current span's context, in a copy of {@code metadata}. When no span is current (a
     * handler continuing on another thread), the trace context of the handled message, so the
     * trace goes on. With neither, {@code metadata} unchanged.
     */
    @Override
    public Metadata correlate(Metadata metadata, Message<?> handledMessage) {
        var current = Context.current();
        if (Span.fromContext(current).getSpanContext().isValid()) {
            var out = copy(metadata);
            propagator.inject(current, out, (carrier, key, value) -> carrier.put(key, value));
            return out;
        }
        var handled = handledMessage == null ? null : handledMessage.getMetadata();
        if (handled == null || !handled.containsKey("traceparent")) return metadata;
        var out = copy(metadata);
        for (var key : TRACE_KEYS) {
            var value = handled.get(key);
            if (value != null) out.put(key, value);
        }
        return out;
    }

    private static Metadata copy(Metadata metadata) {
        var out = new Metadata();
        if (metadata != null) out.putAll(metadata);
        return out;
    }
}
