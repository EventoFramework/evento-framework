package com.evento.demo.telemetry;

import io.sentry.*;
import io.sentry.exception.InvalidSentryTraceHeaderException;
import com.evento.application.performance.TracingAgent;
import com.evento.application.performance.Track;
import com.evento.common.modeling.messaging.message.application.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import static io.sentry.BaggageHeader.BAGGAGE_HEADER;
import static io.sentry.SentryTraceHeader.SENTRY_TRACE_HEADER;

public class SentryTracingAgent extends TracingAgent {

    public SentryTracingAgent(String bundleId, long bundleVersion, String sentryDns) {
        super(bundleId, bundleVersion);
        Sentry.init(options -> {
            options.setDsn(sentryDns);
            options.setEnableTracing(true);
            options.setTracesSampleRate(1.0);
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T doTrack(Message<?> message, String component,
                            Track trackingAnnotation,
                            Transaction<T> transaction) throws Throwable {
        if (message == null) return transaction.run();
        var metadata = message.getMetadata();
        if (metadata == null) {
            metadata = new Metadata();
        }
        ITransaction t;
        var action = switch (message) {
            case CommandMessage<?> ignored -> "handleCommand";
            case QueryMessage<?>  ignored -> "handleQuery";
            case EventMessage<?>  ignored -> "onEvent";
            case InvocationMessage i -> i.getAction();
            default -> "invoke";
        };
        if (metadata.containsKey(SENTRY_TRACE_HEADER)) {
            try {
                t = Sentry.startTransaction(
                        TransactionContext.fromSentryTrace(
                                component + "." + action + "(" + message.getPayloadName() + ")",
                                "evento",
                                new SentryTraceHeader(
                                        metadata.get(SENTRY_TRACE_HEADER)
                                )
                        )
                );
            } catch (InvalidSentryTraceHeaderException e) {
                return transaction.run();

            }
        } else {
            if (trackingAnnotation != null) {

                t = Sentry.startTransaction(
                        component + "." + action + "(" + message.getPayloadName() + ")",
                        "evento");
            } else {
                try {
                    return transaction.run();
                } catch (Exception e) {
                    if (e.getCause() instanceof RuntimeException re) {
                        re.setStackTrace(Stream.concat(
                                Stream.of(re.getStackTrace()),
                                Stream.of(new RuntimeException().getStackTrace())
                        ).toArray(StackTraceElement[]::new));
                        throw re;
                    }
                    throw e;
                }

            }
        }
        metadata.put(SENTRY_TRACE_HEADER, t.toSentryTrace().getValue());
        var b = t.toBaggageHeader(List.of());
        if (b != null)
            metadata.put(BAGGAGE_HEADER, b.getValue());
        t.setData("Description", t.getName() + " - " + getBundleId() + "@" + getBundleVersion());
        t.setTag("message", message.getPayloadName());
        t.setTag("component", component);
        t.setTag("bundle", getBundleId());
        t.setTag("bundleVersion", String.valueOf(getBundleVersion()));
        if (message instanceof DomainCommandMessage cm) {
            t.setData("AggregateId", cm.getAggregateId());
        }
        message.setMetadata(metadata);
        try {
            var resp = transaction.run();
            if (resp instanceof CompletableFuture<?> c) {
                resp = (T) c.thenApply(o -> {
                    t.finish(SpanStatus.OK);
                    return o;
                }).exceptionally(tr -> {
                    t.setThrowable(tr);
                    t.setData("Payload", message.getSerializedPayload().getSerializedObject());
                    t.finish(SpanStatus.INTERNAL_ERROR);
                    Sentry.captureException(tr);
                    System.out.println(t.toSentryTrace().getTraceId());
                    throw new CompletionException(tr);
                });
            } else {
                t.finish(SpanStatus.OK);
            }
            return resp;
        } catch (Throwable tr) {
            t.setThrowable(tr);
            t.setData("Payload", message.getSerializedPayload().getSerializedObject());
            t.finish(SpanStatus.INTERNAL_ERROR);
            Sentry.captureException(tr);
            System.out.println(t.toSentryTrace().getTraceId());
            throw tr;
        }
    }

    @Override
    public Metadata correlate(Metadata metadata, Message<?> handledMessage) {
        if (handledMessage == null)
            return metadata;
        if (handledMessage.getMetadata() != null && handledMessage.getMetadata().containsKey(SENTRY_TRACE_HEADER)) {
            if (metadata == null) {
                metadata = new Metadata();
            }
            metadata.put(SENTRY_TRACE_HEADER, handledMessage.getMetadata().get(SENTRY_TRACE_HEADER));
        }
        if (handledMessage.getMetadata() != null && handledMessage.getMetadata().containsKey(BAGGAGE_HEADER)) {
            if (metadata == null) {
                metadata = new Metadata();
            }
            metadata.put(BAGGAGE_HEADER, handledMessage.getMetadata().get(BAGGAGE_HEADER));
        }
        return metadata;
    }


}
