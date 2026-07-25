package com.evento.application.manager;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.application.CommandMessage;
import com.evento.common.modeling.messaging.message.application.QueryMessage;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.modeling.messaging.payload.ServiceEvent;
import com.evento.common.modeling.messaging.query.QueryResponse;
import com.evento.common.modeling.state.AggregateState;
import com.evento.common.modeling.state.SagaState;
import com.evento.common.utils.ProjectorStatus;

/**
 * Hooks around every handler invocation. The common production use is transaction
 * management: open a transaction in {@code before…}, commit in {@code after…}, roll back in
 * {@code onException…}.
 *
 * <h2>Thread-affinity guarantee (what makes ThreadLocal-bound transactions work)</h2>
 * <p>For a single event, {@code before… → handler → after…/onException…} always run on
 * <b>one and the same thread</b>. This holds for parallel consumers too: when an
 * {@code @EventHandler(executor = "...")} is dispatched to a
 * {@link com.evento.common.messaging.consumer.ConsumerExecutor}, the <em>whole</em> chain
 * runs on that executor's task thread — the consumer's fetch loop never runs any part of
 * it. A transaction bound to a {@code ThreadLocal} (Spring's
 * {@code TransactionSynchronizationManager}, for instance) therefore behaves identically
 * inline and in parallel. Each retry attempt gets its own {@code before…}/{@code after…}
 * pair, and so its own transaction.
 *
 * <h2>Implementations must be thread-safe</h2>
 * <p>One instance is shared by every consumer in the bundle, and with parallel consumers it
 * is entered concurrently by many threads. Keep per-invocation state in {@link ThreadLocal}s
 * or method locals — never in instance fields.
 *
 * <p>Unbind whatever you bound in a {@code finally}. The framework writes a failed event to
 * the dead-event queue immediately after {@code onException…} returns, on the same thread;
 * if a rolled-back transaction were still bound, that write would join it.
 *
 * <p>All methods are {@code default} no-ops: implement only the hooks you need.
 */
public interface MessageHandlerInterceptor {
    default void beforeAggregateCommandHandling(
            Object aggregate,
            AggregateState aggregateState,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway
    ) {}

    default DomainEvent afterAggregateCommandHandling(
            Object aggregate,
            AggregateState aggregateState,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway,
            DomainEvent event
    ) { return event; }

    default Throwable onExceptionAggregateCommandHandling(
            Object aggregate,
            AggregateState aggregateState,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway,
            Throwable throwable
    ) { return throwable; }

    default void beforeServiceCommandHandling(
            Object service,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway
    ) {}

    default ServiceEvent afterServiceCommandHandling(
            Object service,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway,
            ServiceEvent event
    ) { return event; }

    default Throwable onExceptionServiceCommandHandling(
            Object service,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway,
            Throwable t) { return t; }

    default void beforeProjectionQueryHandling(
            Object projection,
            QueryMessage<?> commandMessage,
            QueryGateway queryGateway
    ) {}

    default QueryResponse<?> afterProjectionQueryHandling(
            Object projection,
            QueryMessage<?> commandMessage,
            QueryGateway queryGateway,
            QueryResponse<?> response
    ) { return response; }

    default Throwable onExceptionProjectionQueryHandling(
            Object projection,
            QueryMessage<?> commandMessage,
            QueryGateway queryGateway,
            Throwable t
    ) { return t; }

    default void beforeProjectorEventHandling(Object projector,
                                      PublishedEvent publishedEvent,
                                      CommandGateway commandGateway,
                                      QueryGateway queryGateway,
                                      ProjectorStatus projectorStatus) {}

    default void afterProjectorEventHandling(Object projector,
                                     PublishedEvent publishedEvent,
                                     CommandGateway commandGateway,
                                     QueryGateway queryGateway,
                                     ProjectorStatus projectorStatus) {}

    default Throwable onExceptionProjectorEventHandling(Object projector,
                                                PublishedEvent publishedEvent,
                                                CommandGateway commandGateway,
                                                QueryGateway queryGateway,
                                                ProjectorStatus projectorStatus,
                                                Throwable t) { return t; }

    default void beforeSagaEventHandling(Object saga,
                                 PublishedEvent publishedEvent,
                                 CommandGateway commandGateway,
                                 QueryGateway queryGateway,
                                 SagaState sagaState) {}

    default SagaState afterSagaEventHandling(Object saga,
                                     PublishedEvent publishedEvent,
                                     CommandGateway commandGateway,
                                     QueryGateway queryGateway,
                                     SagaState sagaState) { return sagaState; }

    default Throwable onExceptionSagaEventHandling(Object saga,
                                           PublishedEvent publishedEvent,
                                           CommandGateway commandGateway,
                                           QueryGateway queryGateway,
                                           SagaState sagaState,
                                           Throwable t) { return t; }

    default void beforeObserverEventHandling(Object observer,
                                     PublishedEvent publishedEvent,
                                     CommandGateway commandGateway,
                                     QueryGateway queryGateway) {}

    default void afterObserverEventHandling(Object observer,
                                    PublishedEvent publishedEvent,
                                    CommandGateway commandGateway,
                                    QueryGateway queryGateway) {}

    default Throwable onExceptionObserverEventHandling(Object observer,
                                               PublishedEvent publishedEvent,
                                               CommandGateway commandGateway,
                                               QueryGateway queryGateway,
                                               Throwable t) { return t; }
}
