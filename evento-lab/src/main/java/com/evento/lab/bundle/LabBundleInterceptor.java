package com.evento.lab.bundle;

import com.evento.application.manager.MessageHandlerInterceptor;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.application.CommandMessage;
import com.evento.common.modeling.messaging.message.application.QueryMessage;
import com.evento.common.modeling.messaging.payload.ServiceEvent;
import com.evento.common.modeling.messaging.query.QueryResponse;
import com.evento.common.modeling.state.AggregateState;
import com.evento.common.modeling.state.SagaState;
import com.evento.common.utils.ProjectorStatus;
import com.evento.lab.api.command.LabTimeoutCommand;
import com.evento.lab.api.command.UpdateOrderCommand;
import com.evento.lab.api.query.FindOrderByIdQuery;

/**
 * Interceptor wired into the lab bundle to exercise all BEFORE_HANDLING and
 * AFTER_HANDLING failure stages across the full command/query RTT pipeline.
 *
 * <p>Failure is triggered by flags on the payload itself, so each test can
 * independently control which stage it wants to fail without global state.
 */
public class LabBundleInterceptor implements MessageHandlerInterceptor {

    // ── Aggregate command ──────────────────────────────────────────────────

    @Override
    public void beforeAggregateCommandHandling(Object aggregate, AggregateState state,
            CommandMessage<?> msg, CommandGateway cg, QueryGateway qg) {
        if (msg.getPayload() instanceof UpdateOrderCommand c && c.isFailBeforeHandling()) {
            throw new RuntimeException("LabBundleInterceptor: BEFORE aggregate command handling");
        }
    }

    @Override
    public com.evento.common.modeling.messaging.payload.DomainEvent afterAggregateCommandHandling(
            Object aggregate, AggregateState state, CommandMessage<?> msg,
            CommandGateway cg, QueryGateway qg,
            com.evento.common.modeling.messaging.payload.DomainEvent event) {
        if (msg.getPayload() instanceof UpdateOrderCommand c && c.isFailAfterHandling()) {
            throw new RuntimeException("LabBundleInterceptor: AFTER aggregate command handling");
        }
        return event;
    }

    // ── Service command ────────────────────────────────────────────────────

    @Override
    public void beforeServiceCommandHandling(Object service, CommandMessage<?> msg,
            CommandGateway cg, QueryGateway qg) {
        if (msg.getPayload() instanceof LabTimeoutCommand c && c.isFailBeforeHandling()) {
            throw new RuntimeException("LabBundleInterceptor: BEFORE service command handling");
        }
    }

    @Override
    public ServiceEvent afterServiceCommandHandling(Object service, CommandMessage<?> msg,
            CommandGateway cg, QueryGateway qg, ServiceEvent event) {
        if (msg.getPayload() instanceof LabTimeoutCommand c && c.isFailAfterHandling()) {
            throw new RuntimeException("LabBundleInterceptor: AFTER service command handling");
        }
        return event;
    }

    // ── Query ─────────────────────────────────────────────────────────────

    @Override
    public void beforeProjectionQueryHandling(Object projection, QueryMessage<?> msg, QueryGateway qg) {
        if (msg.getPayload() instanceof FindOrderByIdQuery q && q.isFailBeforeHandling()) {
            throw new RuntimeException("LabBundleInterceptor: BEFORE query handling");
        }
    }

    @Override
    public QueryResponse<?> afterProjectionQueryHandling(Object projection, QueryMessage<?> msg,
            QueryGateway qg, QueryResponse<?> response) {
        if (msg.getPayload() instanceof FindOrderByIdQuery q && q.isFailAfterHandling()) {
            throw new RuntimeException("LabBundleInterceptor: AFTER query handling");
        }
        return response;
    }

    // ── Projector ─────────────────────────────────────────────────────────

    @Override
    public void beforeProjectorEventHandling(Object projector, PublishedEvent event,
            CommandGateway cg, QueryGateway qg, ProjectorStatus status) {
        if (hasFlag(event, "failBeforeProjector")) {
            throw new RuntimeException("LabBundleInterceptor: BEFORE projector event handling");
        }
    }

    @Override
    public void afterProjectorEventHandling(Object projector, PublishedEvent event,
            CommandGateway cg, QueryGateway qg, ProjectorStatus status) {
        if (hasFlag(event, "failAfterProjector")) {
            throw new RuntimeException("LabBundleInterceptor: AFTER projector event handling");
        }
    }

    // ── Saga ──────────────────────────────────────────────────────────────

    @Override
    public void beforeSagaEventHandling(Object saga, PublishedEvent event,
            CommandGateway cg, QueryGateway qg, SagaState state) {
        if (hasFlag(event, "failBeforeSaga")) {
            throw new RuntimeException("LabBundleInterceptor: BEFORE saga event handling");
        }
    }

    @Override
    public SagaState afterSagaEventHandling(Object saga, PublishedEvent event,
            CommandGateway cg, QueryGateway qg, SagaState state) {
        if (hasFlag(event, "failAfterSaga")) {
            throw new RuntimeException("LabBundleInterceptor: AFTER saga event handling");
        }
        return state;
    }

    // ── Observer ──────────────────────────────────────────────────────────

    @Override
    public void beforeObserverEventHandling(Object observer, PublishedEvent event,
            CommandGateway cg, QueryGateway qg) {
        if (hasFlag(event, "failBeforeObserver")) {
            throw new RuntimeException("LabBundleInterceptor: BEFORE observer event handling");
        }
    }

    @Override
    public void afterObserverEventHandling(Object observer, PublishedEvent event,
            CommandGateway cg, QueryGateway qg) {
        if (hasFlag(event, "failAfterObserver")) {
            throw new RuntimeException("LabBundleInterceptor: AFTER observer event handling");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static boolean hasFlag(PublishedEvent event, String key) {
        var meta = event.getEventMessage().getMetadata();
        return meta != null && "true".equals(meta.get(key));
    }
}
