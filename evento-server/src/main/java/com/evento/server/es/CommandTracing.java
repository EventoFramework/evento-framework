package com.evento.server.es;

import com.evento.common.modeling.messaging.message.application.Metadata;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;

import java.util.Locale;

/**
 * The broker's span for a command it executes: what the server adds to a trace is the part only
 * it sees (lock wait, story fetch, append), as span attributes from the {@link CommandPhaseMetrics}
 * clock.
 *
 * <p>The parent is the {@code traceparent} the caller put in the command's metadata (see
 * {@code OpenTelemetryTracingAgent}); for an aggregate command the span is written back into the
 * metadata of the command forwarded to the bundle, so the handler's span is its child. Service
 * commands are forwarded as received (no re-encoding), so their handler span is a sibling.
 * Queries and other forwarded requests carry opaque bytes and get no server span.
 *
 * <p>Uses {@link GlobalOpenTelemetry}: a no-op unless the OpenTelemetry Java agent runs with the
 * server, or an {@code OpenTelemetry} bean is set, so it costs nothing in a server without
 * tracing ({@code evento.server.tracing.enabled=false} turns it off entirely).
 */
final class CommandTracing {

    static final String SCOPE = "com.evento.server";

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

    /** No spans at all. */
    static final CommandTracing OFF = new CommandTracing(null);

    private final Tracer tracer;
    private final TextMapPropagator propagator;

    private CommandTracing(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry == null ? null : openTelemetry.getTracer(SCOPE);
        this.propagator = openTelemetry == null ? null : openTelemetry.getPropagators().getTextMapPropagator();
    }

    static CommandTracing of(OpenTelemetry openTelemetry) {
        return new CommandTracing(openTelemetry);
    }

    static CommandTracing global() {
        return new CommandTracing(GlobalOpenTelemetry.get());
    }

    /** A span for executing a command of {@code payloadType}, child of the caller's context in {@code metadata}. */
    Span start(String kind, String payloadType, String correlationId, Metadata metadata) {
        if (tracer == null) return Span.getInvalid();
        var parent = metadata == null ? Context.root() : propagator.extract(Context.root(), metadata, GETTER);
        return tracer.spanBuilder("evento " + kind + " " + payloadType)
                .setParent(parent)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("messaging.system", "evento")
                .setAttribute("evento.payload", payloadType)
                .setAttribute("evento.correlation_id", correlationId == null ? "" : correlationId)
                .startSpan();
    }

    /** {@code metadata} with {@code span} as the trace context; unchanged when the span is not recorded. */
    Metadata inject(Span span, Metadata metadata) {
        if (tracer == null || !span.getSpanContext().isValid()) return metadata;
        var out = new Metadata();
        if (metadata != null) out.putAll(metadata);
        propagator.inject(Context.root().with(span), out, (carrier, key, value) -> carrier.put(key, value));
        return out;
    }

    /** The phase durations as {@code evento.phase.<phase>.ms} attributes. */
    static void phases(Span span, CommandPhaseMetrics.Clock clock, boolean keyed) {
        if (!span.isRecording()) return;
        span.setAttribute("evento.keyed", keyed);
        if (clock == null) return;
        for (var phase : CommandPhaseMetrics.Phase.values()) {
            long nanos = clock.nanos[phase.ordinal()];
            if (nanos > 0) {
                span.setAttribute("evento.phase." + phase.name().toLowerCase(Locale.ROOT) + ".ms", nanos / 1_000_000.0);
            }
        }
    }

    static void fail(Span span, Throwable error) {
        if (!span.isRecording()) return;
        span.recordException(error);
        span.setStatus(StatusCode.ERROR, error.getClass().getSimpleName());
    }
}
