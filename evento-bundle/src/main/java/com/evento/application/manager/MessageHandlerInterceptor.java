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
    void beforeAggregateCommandHandling(
            Object aggregate,
            AggregateState aggregateState,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway
    );

    DomainEvent afterAggregateCommandHandling(
            Object aggregate,
            AggregateState aggregateState,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway,
            DomainEvent event
    );

    Throwable onExceptionAggregateCommandHandling(
            Object aggregate,
            AggregateState aggregateState,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway,
            Throwable throwable
    );

    void beforeServiceCommandHandling(
            Object service,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway
    );

    ServiceEvent afterServiceCommandHandling(
            Object service,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway,
            ServiceEvent event
    );

    Throwable onExceptionServiceCommandHandling(
            Object service,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway,
            Throwable t);

    void beforeProjectionQueryHandling(
            Object projection,
            QueryMessage<?> commandMessage,
            QueryGateway queryGateway
    );

    QueryResponse<?> afterProjectionQueryHandling(
            Object projection,
            QueryMessage<?> commandMessage,
            QueryGateway queryGateway,
            QueryResponse<?> response
    );

    Throwable onExceptionProjectionQueryHandling(
            Object projection,
            QueryMessage<?> commandMessage,
            QueryGateway queryGateway,
            Throwable t
    );

    void beforeProjectorEventHandling(Object projector,
                                      PublishedEvent publishedEvent,
                                      CommandGateway commandGateway,
                                      QueryGateway queryGateway,
                                      ProjectorStatus projectorStatus);

    void afterProjectorEventHandling(Object projector,
                                     PublishedEvent publishedEvent,
                                     CommandGateway commandGateway,
                                     QueryGateway queryGateway,
                                     ProjectorStatus projectorStatus);

    Throwable onExceptionProjectorEventHandling(Object projector,
                                                PublishedEvent publishedEvent,
                                                CommandGateway commandGateway,
                                                QueryGateway queryGateway,
                                                ProjectorStatus projectorStatus,
                                                Throwable t);

    void beforeSagaEventHandling(Object saga,
                                 PublishedEvent publishedEvent,
                                 CommandGateway commandGateway,
                                 QueryGateway queryGateway,
                                 SagaState sagaState);

    SagaState afterSagaEventHandling(Object saga,
                                     PublishedEvent publishedEvent,
                                     CommandGateway commandGateway,
                                     QueryGateway queryGateway,
                                     SagaState sagaState);

    Throwable onExceptionSagaEventHandling(Object saga,
                                           PublishedEvent publishedEvent,
                                           CommandGateway commandGateway,
                                           QueryGateway queryGateway,
                                           SagaState sagaState,
                                           Throwable t);

    void beforeObserverEventHandling(Object observer,
                                     PublishedEvent publishedEvent,
                                     CommandGateway commandGateway,
                                     QueryGateway queryGateway);

    void afterObserverEventHandling(Object observer,
                                    PublishedEvent publishedEvent,
                                    CommandGateway commandGateway,
                                    QueryGateway queryGateway);

    Throwable onExceptionObserverEventHandling(Object observer,
                                               PublishedEvent publishedEvent,
                                               CommandGateway commandGateway,
                                               QueryGateway queryGateway,
                                               Throwable t);
}
