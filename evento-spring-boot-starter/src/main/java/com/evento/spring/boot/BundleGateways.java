package com.evento.spring.boot;

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
 * The {@link CommandGateway} and {@link QueryGateway} beans: they exist from the start of the
 * context, so any bean can inject them, and forward to the running bundle's gateways. A call
 * before the bundle runs fails with {@link IllegalStateException}.
 */
final class BundleGateways {

    private BundleGateways() {
    }

    record Commands(EventoBundleLifecycle lifecycle) implements CommandGateway {

        private CommandGateway target() {
            return lifecycle.requireBundle().getCommandGateway();
        }

        @Override
        public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
                                             long timeout, TimeUnit unit) {
            return target().send(command, metadata, handledMessage, timeout, unit);
        }

        @Override
        public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
                                             long timeout, TimeUnit unit, String idempotencyKey) {
            return target().send(command, metadata, handledMessage, timeout, unit, idempotencyKey);
        }

        @Override
        public <R> CompletableFuture<Tracked<R>> sendTracked(Command command, Metadata metadata,
                                                             Message<?> handledMessage, long timeout, TimeUnit unit,
                                                             String idempotencyKey) {
            return target().sendTracked(command, metadata, handledMessage, timeout, unit, idempotencyKey);
        }

        @Override
        public boolean supportsIdempotencyKeys() {
            return target().supportsIdempotencyKeys();
        }

        @Override
        public long getDefaultTimeoutTime() {
            return target().getDefaultTimeoutTime();
        }

        @Override
        public TimeUnit getDefaultTimeoutUnit() {
            return target().getDefaultTimeoutUnit();
        }
    }

    record Queries(EventoBundleLifecycle lifecycle) implements QueryGateway {

        private QueryGateway target() {
            return lifecycle.requireBundle().getQueryGateway();
        }

        @Override
        public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, Metadata metadata,
                                                                      Message<?> handledMessage, long timeout,
                                                                      TimeUnit unit) {
            return target().query(query, metadata, handledMessage, timeout, unit);
        }

        @Override
        public <T extends QueryResponse<?>> CompletableFuture<T> queryWith(Query<T> query, QueryOptions options) {
            return target().queryWith(query, options);
        }

        @Override
        public <V extends View> QueryStream<V> stream(Query<? extends QueryResponse<V>> query, QueryOptions options) {
            return target().stream(query, options);
        }

        @Override
        public long getDefaultTimeoutTime() {
            return target().getDefaultTimeoutTime();
        }

        @Override
        public TimeUnit getDefaultTimeoutUnit() {
            return target().getDefaultTimeoutUnit();
        }
    }
}
