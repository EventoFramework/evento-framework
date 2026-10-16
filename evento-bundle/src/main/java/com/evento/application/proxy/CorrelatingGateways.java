package com.evento.application.proxy;

import com.evento.application.performance.TracingAgent;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.messaging.gateway.QueryOptions;
import com.evento.common.messaging.gateway.QueryStream;
import com.evento.common.messaging.gateway.Tracked;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.payload.Command;
import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.payload.View;
import com.evento.common.modeling.messaging.query.QueryResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The gateways {@code EventoBundle} hands to application code (a REST controller, a scheduled
 * job): every message they send gets its metadata through {@link TracingAgent#correlate}, as the
 * gateways passed to handlers already do ({@link GatewayTelemetryProxy}). That is how a trace
 * started outside Evento (an HTTP request) reaches the handlers.
 *
 * <p>Only used when the bundle has a tracing agent other than the no-op base class, so a bundle
 * without tracing calls its gateways directly.
 */
public final class CorrelatingGateways {

    private CorrelatingGateways() {
    }

    /** {@code gateway} unchanged when {@code agent} is the no-op base class. */
    public static CommandGateway commands(CommandGateway gateway, TracingAgent agent) {
        if (gateway == null || agent == null || agent.getClass() == TracingAgent.class) return gateway;
        return new Commands(gateway, agent);
    }

    /** {@code gateway} unchanged when {@code agent} is the no-op base class. */
    public static QueryGateway queries(QueryGateway gateway, TracingAgent agent) {
        if (gateway == null || agent == null || agent.getClass() == TracingAgent.class) return gateway;
        return new Queries(gateway, agent);
    }

    private record Commands(CommandGateway target, TracingAgent agent) implements CommandGateway {
        @Override
        public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
                                             long timeout, TimeUnit unit) {
            return target.send(command, agent.correlate(metadata, handledMessage), handledMessage, timeout, unit);
        }

        @Override
        public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
                                             long timeout, TimeUnit unit, String idempotencyKey) {
            return target.send(command, agent.correlate(metadata, handledMessage), handledMessage, timeout, unit,
                    idempotencyKey);
        }

        @Override
        public <R> CompletableFuture<Tracked<R>> sendTracked(Command command, Metadata metadata,
                                                             Message<?> handledMessage, long timeout, TimeUnit unit,
                                                             String idempotencyKey) {
            return target.sendTracked(command, agent.correlate(metadata, handledMessage), handledMessage, timeout,
                    unit, idempotencyKey);
        }

        @Override
        public boolean supportsIdempotencyKeys() {
            return target.supportsIdempotencyKeys();
        }

        @Override
        public long getDefaultTimeoutTime() {
            return target.getDefaultTimeoutTime();
        }

        @Override
        public TimeUnit getDefaultTimeoutUnit() {
            return target.getDefaultTimeoutUnit();
        }
    }

    private record Queries(QueryGateway target, TracingAgent agent) implements QueryGateway {
        @Override
        public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, Metadata metadata,
                                                                      Message<?> handledMessage, long timeout,
                                                                      TimeUnit unit) {
            return target.query(query, agent.correlate(metadata, handledMessage), handledMessage, timeout, unit);
        }

        @Override
        public <T extends QueryResponse<?>> CompletableFuture<T> queryWith(Query<T> query, QueryOptions options) {
            return target.queryWith(query, correlated(options));
        }

        @Override
        public <V extends View> QueryStream<V> stream(Query<? extends QueryResponse<V>> query, QueryOptions options) {
            return target.stream(query, correlated(options));
        }

        private QueryOptions correlated(QueryOptions options) {
            var opts = options == null ? QueryOptions.defaults() : options;
            return opts.metadata(agent.correlate(opts.getMetadata(), null));
        }

        @Override
        public long getDefaultTimeoutTime() {
            return target.getDefaultTimeoutTime();
        }

        @Override
        public TimeUnit getDefaultTimeoutUnit() {
            return target.getDefaultTimeoutUnit();
        }
    }
}
