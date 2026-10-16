package com.evento.test;

import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.QueryResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A {@link QueryGateway} answered by stubs ({@link #answer}) and recording every query. A query
 * without a stub fails, so a test never passes on a {@code null} answer it did not mean.
 */
public final class StubQueryGateway implements QueryGateway {

    private final List<Query<?>> asked = new ArrayList<>();
    private final Map<Class<?>, Function<Object, Object>> answers = new HashMap<>();

    /** Answer queries of {@code type} with what {@code answer} returns; throw from it to fail the query. */
    @SuppressWarnings("unchecked")
    public <R extends QueryResponse<?>, Q extends Query<R>> StubQueryGateway answer(Class<Q> type, Function<Q, R> answer) {
        answers.put(type, q -> answer.apply((Q) q));
        return this;
    }

    /** The queries asked so far, in order. */
    public List<Query<?>> asked() {
        return List.copyOf(asked);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, Metadata metadata,
                                                                  Message<?> handledMessage, long timeout, TimeUnit unit) {
        asked.add(query);
        var answer = answers.get(query.getClass());
        if (answer == null) {
            return CompletableFuture.failedFuture(new AssertionError(
                    "no answer stubbed for " + query.getClass().getName() + "; add one with answer(...)"));
        }
        try {
            return CompletableFuture.completedFuture((T) answer.apply(query));
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }
}
