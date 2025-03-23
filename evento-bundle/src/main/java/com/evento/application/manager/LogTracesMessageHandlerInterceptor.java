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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LogTracesMessageHandlerInterceptor implements MessageHandlerInterceptor {

    private final Logger logger = LogManager.getLogger(LogTracesMessageHandlerInterceptor.class);

    @Override
    public void beforeAggregateCommandHandling(
            Object aggregate,
            AggregateState aggregateState,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway
    ) {
        logger.trace("Handling Domain Command {} in Aggregate {}",
                commandMessage.getPayload(), aggregate.getClass().getSimpleName());
    }

    @Override
    public DomainEvent afterAggregateCommandHandling(
            Object aggregate,
            AggregateState aggregateState,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway,
            DomainEvent event
    ) {
        logger.trace("Handled Domain Command {} in Aggregate {} and Produced Domain Event {}",
                commandMessage.getPayload(), aggregate.getClass().getSimpleName(), event);
        return event;
    }

    @Override
    public Throwable onExceptionAggregateCommandHandling(
            Object aggregate,
            AggregateState aggregateState,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway,
            Throwable throwable
    ) {
        logger.trace("Error During Handling for Domain Command {} in Aggregate {}",
                commandMessage.getPayload(), aggregate.getClass().getSimpleName(), throwable);
        return throwable;
    }

    @Override
    public void beforeServiceCommandHandling(
            Object service,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway
    ) {
        logger.trace("Handling Service Command {} in Service {}",
                commandMessage.getPayload(), service.getClass().getSimpleName());
    }

    @Override
    public ServiceEvent afterServiceCommandHandling(
            Object service,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway,
            ServiceEvent event
    ) {
        logger.trace("Handled Service Command {} in Service {} and Produced Service Event {}",
                commandMessage.getPayload(), service.getClass().getSimpleName(), event);
        return event;
    }

    @Override
    public Throwable onExceptionServiceCommandHandling(
            Object service,
            CommandMessage<?> commandMessage,
            CommandGateway commandGateway,
            QueryGateway queryGateway,
            Throwable throwable) {
        logger.trace("Error During Handling for Service Command {} in Service {}",
                commandMessage.getPayload(), service.getClass().getSimpleName(), throwable);
        return throwable;
    }


    @Override
    public void beforeProjectionQueryHandling(
            Object projection,
            QueryMessage<?> commandMessage,
            QueryGateway queryGateway
    ) {
        logger.trace("Handling Query {} in Projection {}",
                commandMessage.getPayload(), projection.getClass().getSimpleName());
    }

    @Override
    public QueryResponse<?> afterProjectionQueryHandling(
            Object projection,
            QueryMessage<?> commandMessage,
            QueryGateway queryGateway,
            QueryResponse<?> response
    ) {
        logger.trace("Handled Query {} in Projection {} and Produced Query Response {}",
                commandMessage.getPayload(), projection.getClass().getSimpleName(), response);
        return response;
    }

    @Override
    public Throwable onExceptionProjectionQueryHandling(
            Object projection,
            QueryMessage<?> commandMessage,
            QueryGateway queryGateway,
            Throwable throwable
    ) {
        logger.trace("Error During Handling for Query {} in Projection {}",
                commandMessage.getPayload(), projection.getClass().getSimpleName(), throwable);
        return throwable;
    }

    @Override
    public void beforeProjectorEventHandling(Object projector,
                                             PublishedEvent publishedEvent,
                                             CommandGateway commandGateway,
                                             QueryGateway queryGateway,
                                             ProjectorStatus projectorStatus) {
        logger.trace("Handling Event {} in Projector {}",
                publishedEvent.getEventMessage().getPayload(), projector.getClass().getSimpleName());
    }

    @Override
    public void afterProjectorEventHandling(Object projector,
                                            PublishedEvent publishedEvent,
                                            CommandGateway commandGateway,
                                            QueryGateway queryGateway,
                                            ProjectorStatus projectorStatus) {
        logger.trace("Handled Event {} in Projector {}",
                publishedEvent.getEventMessage().getPayload(), projector.getClass().getSimpleName());

    }

    @Override
    public Throwable onExceptionProjectorEventHandling(Object projector,
                                                       PublishedEvent publishedEvent,
                                                       CommandGateway commandGateway,
                                                       QueryGateway queryGateway,
                                                       ProjectorStatus projectorStatus,
                                                       Throwable t) {
        logger.trace("Error During Handling for Event {} in Projector {}",
                publishedEvent.getEventMessage().getPayload(), projector.getClass().getSimpleName());
        return t;
    }

    @Override
    public void beforeSagaEventHandling(Object saga,
                                        PublishedEvent publishedEvent,
                                        CommandGateway commandGateway,
                                        QueryGateway queryGateway,
                                        SagaState sagaState) {
        logger.trace("Handled Event {} in Saga {}",
                publishedEvent.getEventMessage().getPayload(), saga.getClass().getSimpleName());
    }

    @Override
    public SagaState afterSagaEventHandling(Object saga,
                                            PublishedEvent publishedEvent,
                                            CommandGateway commandGateway,
                                            QueryGateway queryGateway,
                                            SagaState sagaState) {
        logger.trace("Handling Event {} in Saga {}",
                publishedEvent.getEventMessage().getPayload(), saga.getClass().getSimpleName());
        return sagaState;

    }

    @Override
    public Throwable onExceptionSagaEventHandling(Object saga,
                                                  PublishedEvent publishedEvent,
                                                  CommandGateway commandGateway,
                                                  QueryGateway queryGateway,
                                                  SagaState sagaState,
                                                  Throwable t) {
        logger.trace("Error During Handling for Event {} in Saga {}",
                publishedEvent.getEventMessage().getPayload(), saga.getClass().getSimpleName());
        return t;
    }

    @Override
    public void beforeObserverEventHandling(Object observer,
                                            PublishedEvent publishedEvent,
                                            CommandGateway commandGateway,
                                            QueryGateway queryGateway) {
        logger.trace("Handled Event {} in Observer {}",
                publishedEvent.getEventMessage().getPayload(), observer.getClass().getSimpleName());
    }

    @Override
    public void afterObserverEventHandling(Object observer,
                                           PublishedEvent publishedEvent,
                                           CommandGateway commandGateway,
                                           QueryGateway queryGateway) {
        logger.trace("Handling Event {} in Observer {}",
                publishedEvent.getEventMessage().getPayload(), observer.getClass().getSimpleName());

    }

    @Override
    public Throwable onExceptionObserverEventHandling(Object observer,
                                                      PublishedEvent publishedEvent,
                                                      CommandGateway commandGateway,
                                                      QueryGateway queryGateway,
                                                      Throwable t) {
        logger.trace("Error During Handling for Event {} in Observer {}",
                publishedEvent.getEventMessage().getPayload(), observer.getClass().getSimpleName());
        return t;
    }
}
