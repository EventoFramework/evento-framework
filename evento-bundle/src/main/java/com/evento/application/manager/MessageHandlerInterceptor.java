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
