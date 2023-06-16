package org.evento.demo.telemetry;

import io.sentry.*;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.Pair;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.messaging.gateway.CommandGatewayImpl;
import org.evento.common.messaging.gateway.QueryGateway;
import org.evento.common.messaging.gateway.QueryGatewayImpl;
import org.evento.common.modeling.messaging.message.application.Message;
import org.evento.common.modeling.messaging.payload.Command;
import org.evento.common.modeling.messaging.payload.Query;
import org.evento.common.modeling.messaging.query.QueryResponse;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static io.sentry.BaggageHeader.BAGGAGE_HEADER;
import static io.sentry.SentryTraceHeader.SENTRY_TRACE_HEADER;

public class SentryMessageGatewayAndCorrelator implements CommandGateway, QueryGateway, BiConsumer<Message<?>, Message<?>> {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;
    public SentryMessageGatewayAndCorrelator(MessageBus messageBus, String serverNodeName, String sentryDns) {
        this.commandGateway = new CommandGatewayImpl(messageBus, serverNodeName);
        this.queryGateway = new QueryGatewayImpl(messageBus, serverNodeName);
        Sentry.init(options -> {
            options.setDsn(sentryDns);
            options.setDebug(true);
            options.setEnableTracing(true);
            options.setTracesSampleRate(1.0);
        });
    }

    private ISpan obtainTransaction(Message<?> handledMessage, HashMap<String, String> metadata) {
        ISpan span = null;
        try {
            if (handledMessage != null && handledMessage.getMetadata() != null
                    && handledMessage.getMetadata().containsKey(SENTRY_TRACE_HEADER))
                span = Sentry.startTransaction(TransactionContext.fromSentryTrace(handledMessage.getPayloadName(),
                        TransactionNameSource.CUSTOM,
                        "invocation",
                        new SentryTraceHeader(handledMessage.getMetadata().get(SENTRY_TRACE_HEADER)),
                        Baggage.fromHeader(handledMessage.getMetadata().get(BAGGAGE_HEADER)),
                        SpanId.EMPTY_ID
                ));
            else if (handledMessage != null) {
                span = Sentry.startTransaction(handledMessage.getPayloadName(), "invocation");
                if(handledMessage.getMetadata() == null){
                    handledMessage.setMetadata(new HashMap<>());
                }
                if(!handledMessage.getMetadata().containsKey(SENTRY_TRACE_HEADER)){
                    handledMessage.getMetadata().put(SENTRY_TRACE_HEADER, span.toSentryTrace().getValue());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (span == null) {
            span = Sentry.startTransaction("transaction", "invocation");
            if(handledMessage.getMetadata() == null){
                handledMessage.setMetadata(new HashMap<>());
            }
            if(!handledMessage.getMetadata().containsKey(SENTRY_TRACE_HEADER)){
                handledMessage.getMetadata().put(SENTRY_TRACE_HEADER, span.toSentryTrace().getValue());
            }
        }
        if (handledMessage != null && handledMessage.getMetadata() != null &&
                handledMessage.getMetadata().containsKey(BAGGAGE_HEADER)) {
            metadata.put(BAGGAGE_HEADER, span.toBaggageHeader(
                    List.of(handledMessage.getMetadata().get(BAGGAGE_HEADER).split(","))).getValue());
        }
        return span;
    }

    @Override
    public <R> R sendAndWait(Command command, HashMap<String, String> metadata, Message<?> handledMessage) {
        metadata = metadata == null ? new HashMap<>() : metadata;
        var parent = obtainTransaction(handledMessage, metadata);
        var span = parent.startChild("command(" + command.getClass().getSimpleName() + ")");
        metadata.put(SENTRY_TRACE_HEADER, span.toSentryTrace().getValue());
        try {
            return commandGateway.sendAndWait(command, metadata, handledMessage);
        } catch (Exception e) {
            span.setData("message", command);
            span.setThrowable(e);
            span.setStatus(SpanStatus.INTERNAL_ERROR);
            throw e;
        } finally {
            parent.finish();
        }
    }



    @Override
    public <R> R sendAndWait(Command command, HashMap<String, String> metadata, Message<?> handledMessage, long timeout, TimeUnit unit) {
        metadata = metadata == null ? new HashMap<>() : metadata;
        var parent = obtainTransaction(handledMessage, metadata);
        var span = parent.startChild("command(" + command.getClass().getSimpleName() + ")");
        metadata.put(SENTRY_TRACE_HEADER, span.toSentryTrace().getValue());
        try {
            return commandGateway.sendAndWait(command, metadata, handledMessage, timeout, unit);
        } catch (Exception e) {
            span.setData("message", command);
            span.setThrowable(e);
            span.setStatus(SpanStatus.INTERNAL_ERROR);
            throw e;
        } finally {
            parent.finish();
        }
    }

    @Override
    public <R> CompletableFuture<R> send(Command command, HashMap<String, String> metadata, Message<?> handledMessage) {
        metadata = metadata == null ? new HashMap<>() : metadata;
        var parent = obtainTransaction(handledMessage, metadata);
        var span = parent.startChild("command(" + command.getClass().getSimpleName() + ")");
        metadata.put(SENTRY_TRACE_HEADER, span.toSentryTrace().getValue());
        try {
            return commandGateway.send(command, metadata, handledMessage);
        } catch (Exception e) {
            span.setData("message", command);
            span.setThrowable(e);
            span.setStatus(SpanStatus.INTERNAL_ERROR);
            throw e;
        } finally {
            parent.finish();
        }
    }

    @Override
    public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, HashMap<String, String> metadata, Message<?> handledMessage) {
        metadata = metadata == null ? new HashMap<>() : metadata;
        var parent = obtainTransaction(handledMessage, metadata);
        var span = parent.startChild("command(" + query.getClass().getSimpleName() + ")");
        metadata.put(SENTRY_TRACE_HEADER, span.toSentryTrace().getValue());
        try {
            return queryGateway.query(query, metadata, handledMessage);
        } catch (Exception e) {
            span.setData("message", query);
            span.setThrowable(e);
            span.setStatus(SpanStatus.INTERNAL_ERROR);
            throw e;
        } finally {
            parent.finish();
        }

    }

    @Override
    public void accept(Message<?> message, Message<?> message2) {
       if(message.getMetadata() != null && message.getMetadata().containsKey(SENTRY_TRACE_HEADER)){
           if(message2.getMetadata()==null){
               message2.setMetadata(new HashMap<>());
           }
           message2.getMetadata().put(SENTRY_TRACE_HEADER, message.getMetadata().get(SENTRY_TRACE_HEADER));
       }
        if(message.getMetadata() != null && message.getMetadata().containsKey(BAGGAGE_HEADER)){
            if(message2.getMetadata()==null){
                message2.setMetadata(new HashMap<>());
            }
            message2.getMetadata().put(BAGGAGE_HEADER, message.getMetadata().get(BAGGAGE_HEADER));
        }
    }
}
