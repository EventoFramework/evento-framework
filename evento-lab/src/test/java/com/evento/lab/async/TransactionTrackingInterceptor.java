package com.evento.lab.async;

import com.evento.application.manager.MessageHandlerInterceptor;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.utils.ProjectorStatus;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stands in for the production pattern of managing a Postgres transaction from an
 * interceptor: bind something to a {@link ThreadLocal} before the handler, unbind after.
 *
 * <p>That pattern only works if {@code before → handler → after} all run on the same
 * thread. This interceptor records the thread of each phase so the IT can assert exactly
 * that, including when the handler is dispatched to a consumer executor.
 */
public final class TransactionTrackingInterceptor implements MessageHandlerInterceptor {

    private static final ThreadLocal<String> TRANSACTION = new ThreadLocal<>();
    private static final AtomicInteger COUNTER = new AtomicInteger();

    /** Non-null only inside a handler invocation — i.e. only if binding worked. */
    public static String currentTransaction() {
        return TRANSACTION.get();
    }

    public static final AtomicInteger opened = new AtomicInteger();
    public static final AtomicInteger committed = new AtomicInteger();
    public static final AtomicInteger rolledBack = new AtomicInteger();

    public static void reset() {
        opened.set(0);
        committed.set(0);
        rolledBack.set(0);
        COUNTER.set(0);
    }

    @Override
    public void beforeProjectorEventHandling(Object projector,
                                             PublishedEvent event,
                                             CommandGateway commandGateway,
                                             QueryGateway queryGateway,
                                             ProjectorStatus projectorStatus) {
        TRANSACTION.set("tx-" + COUNTER.incrementAndGet());
        opened.incrementAndGet();
        AsyncLabStore.threadNames.put(key(event) + ":before", Thread.currentThread().getName());
    }

    @Override
    public void afterProjectorEventHandling(Object projector,
                                            PublishedEvent event,
                                            CommandGateway commandGateway,
                                            QueryGateway queryGateway,
                                            ProjectorStatus projectorStatus) {
        AsyncLabStore.threadNames.put(key(event) + ":after", Thread.currentThread().getName());
        try {
            committed.incrementAndGet();
        } finally {
            TRANSACTION.remove();
        }
    }

    @Override
    public Throwable onExceptionProjectorEventHandling(Object projector,
                                                       PublishedEvent event,
                                                       CommandGateway commandGateway,
                                                       QueryGateway queryGateway,
                                                       ProjectorStatus projectorStatus,
                                                       Throwable throwable) {
        AsyncLabStore.threadNames.put(key(event) + ":onException", Thread.currentThread().getName());
        try {
            rolledBack.incrementAndGet();
        } finally {
            // Unbinding in a finally is what keeps the framework's subsequent dead-event
            // write off this rolled-back transaction.
            TRANSACTION.remove();
        }
        return throwable;
    }

    private static String key(PublishedEvent event) {
        return event.getAggregateId();
    }
}
