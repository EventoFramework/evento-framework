package com.evento.application;

import com.evento.application.manager.*;
import com.evento.common.documentation.Domain;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.modeling.messaging.query.Multiple;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;

import java.lang.reflect.ParameterizedType;
import java.util.*;

final class HandlerMetadataBuilder {

    record Result(
            List<RegisteredHandler> handlers,
            Map<String, String[]> payloadInfo
    ) {}

    static Result build(
            AggregateManager aggregateManager,
            ServiceManager serviceManager,
            ProjectionManager projectionManager,
            ProjectorManager projectorManager,
            ObserverManager observerManager,
            SagaManager sagaManager,
            InvokerManager invokerManager
    ) throws Exception {
        var handlers = new ArrayList<RegisteredHandler>();
        var payloads = new HashSet<Class<?>>();

        aggregateManager.getHandlers().forEach((k, v) -> {
            var r = v.getAggregateCommandHandler(k).getReturnType().getSimpleName();
            handlers.add(new RegisteredHandler(
                    ComponentType.Aggregate,
                    v.getRef().getClass().getSimpleName(),
                    HandlerType.AggregateCommandHandler,
                    PayloadType.DomainCommand,
                    k, r, false, null
            ));
            var esh = v.getEventSourcingHandler(r);
            if (esh != null) {
                handlers.add(new RegisteredHandler(
                        ComponentType.Aggregate,
                        v.getRef().getClass().getSimpleName(),
                        HandlerType.EventSourcingHandler,
                        PayloadType.DomainEvent,
                        r, null, false, null
                ));
            }
            payloads.add(v.getAggregateCommandHandler(k).getParameterTypes()[0]);
            payloads.add(v.getAggregateCommandHandler(k).getReturnType());
        });

        serviceManager.getHandlers().forEach((k, v) -> {
            var r = v.getAggregateCommandHandler(k).getReturnType().getSimpleName();
            handlers.add(new RegisteredHandler(
                    ComponentType.Service,
                    v.getRef().getClass().getSimpleName(),
                    HandlerType.CommandHandler,
                    PayloadType.ServiceCommand,
                    k, r.equals("void") ? null : r, false, null
            ));
            payloads.add(v.getAggregateCommandHandler(k).getParameterTypes()[0]);
            payloads.add(v.getAggregateCommandHandler(k).getReturnType());
        });

        projectorManager.getHandlers().forEach((k, v) -> v.forEach((k1, v1) -> {
            handlers.add(new RegisteredHandler(
                    ComponentType.Projector,
                    v1.getRef().getClass().getSimpleName(),
                    HandlerType.EventHandler,
                    v1.getEventHandler(k).getParameterTypes()[0].getSuperclass()
                            .isAssignableFrom(DomainEvent.class)
                            ? PayloadType.DomainEvent : PayloadType.ServiceEvent,
                    k, null, false, null
            ));
            payloads.add(v1.getEventHandler(k).getParameterTypes()[0]);
        }));

        observerManager.getHandlers().forEach((k, v) -> v.forEach((k1, v1) -> {
            handlers.add(new RegisteredHandler(
                    ComponentType.Observer,
                    v1.getRef().getClass().getSimpleName(),
                    HandlerType.EventHandler,
                    v1.getEventHandler(k).getParameterTypes()[0].getSuperclass()
                            .isAssignableFrom(DomainEvent.class)
                            ? PayloadType.DomainEvent : PayloadType.ServiceEvent,
                    k, null, false, null
            ));
            payloads.add(v1.getEventHandler(k).getParameterTypes()[0]);
        }));

        sagaManager.getHandlers().forEach((k, v) -> v.forEach((k1, v1) -> {
            handlers.add(new RegisteredHandler(
                    ComponentType.Saga,
                    v1.getRef().getClass().getSimpleName(),
                    HandlerType.SagaEventHandler,
                    v1.getSagaEventHandler(k).getParameterTypes()[0].getSuperclass()
                            .isAssignableFrom(DomainEvent.class)
                            ? PayloadType.DomainEvent : PayloadType.ServiceEvent,
                    k, null, false,
                    v1.getSagaEventHandler(k).getAnnotation(SagaEventHandler.class).associationProperty()
            ));
            payloads.add(v1.getSagaEventHandler(k).getParameterTypes()[0]);
        }));

        projectionManager.getHandlers().forEach((k, v) -> {
            var r = v.getQueryHandler(k).getReturnType();
            handlers.add(new RegisteredHandler(
                    ComponentType.Projection,
                    v.getRef().getClass().getSimpleName(),
                    HandlerType.QueryHandler,
                    PayloadType.Query,
                    k,
                    ((Class<?>) ((ParameterizedType) v.getQueryHandler(k).getGenericReturnType())
                            .getActualTypeArguments()[0]).getSimpleName(),
                    r.isAssignableFrom(Multiple.class),
                    null
            ));
            payloads.add(v.getQueryHandler(k).getParameterTypes()[0]);
            payloads.add((Class<?>) ((ParameterizedType) v.getQueryHandler(k).getGenericReturnType())
                    .getActualTypeArguments()[0]);
        });

        handlers.addAll(invokerManager.getHandlers());

        ObjectMapper mapper = new ObjectMapper();
        JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(mapper);
        var payloadInfo = new HashMap<String, String[]>();
        for (Class<?> p : payloads) {
            if (p == null) continue;
            var info = new String[2];
            payloadInfo.put(p.getSimpleName(), info);
            try {
                info[0] = mapper.writeValueAsString(schemaGen.generateSchema(p));
            } catch (Exception ignored) {}
            if (p.getAnnotation(Domain.class) != null) {
                info[1] = p.getAnnotation(Domain.class).name();
            }
        }

        return new Result(handlers, payloadInfo);
    }
}
