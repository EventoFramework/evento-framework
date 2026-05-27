package com.evento.application;

import com.evento.application.manager.*;
import com.evento.common.documentation.Domain;
import com.evento.common.modeling.annotations.EventoDescription;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.transport.protocol.PayloadDiscoveryInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.*;

final class HandlerMetadataBuilder {

    private static final Logger logger = LogManager.getLogger(HandlerMetadataBuilder.class);

    record Result(
            List<RegisteredHandler> handlers,
            Map<String, PayloadDiscoveryInfo> payloadInfo
    ) {}

    // ── invocation + handler-line scanner ──────────────────────────────────────

    private static void applyInvocations(RegisteredHandler h, Method m) {
        try {
            var result = AsmInvocationScanner.scan(m);
            if (!result.commands().isEmpty()) h.setInvokedCommands(new HashMap<>(result.commands()));
            if (!result.queries().isEmpty())  h.setInvokedQueries(new HashMap<>(result.queries()));
            h.setHandlerLine(result.handlerLine());
        } catch (Exception e) {
            logger.warn("ASM invocation scan failed for {}: {}", m, e.getMessage());
        }
    }

    // ── component-level metadata (path, line, description, detail) ─────────────

    private static final Map<Class<?>, AsmClassMetadataScanner.ClassMetadata> metaCache
            = new IdentityHashMap<>();

    private static void applyComponentMetadata(RegisteredHandler h, Class<?> componentClass) {
        var meta = metaCache.computeIfAbsent(componentClass, c -> {
            try {
                return AsmClassMetadataScanner.scan(c);
            } catch (Exception e) {
                logger.warn("ASM class scan failed for {}: {}", c, e.getMessage());
                return AsmClassMetadataScanner.ClassMetadata.EMPTY;
            }
        });
        h.setComponentDescription(meta.description());
        h.setComponentDetail(meta.detail());
        h.setComponentPath(meta.sourcePath());
        h.setComponentLine(meta.declarationLine());
    }

    // ── build ──────────────────────────────────────────────────────────────────

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
            var m = v.getAggregateCommandHandler(k);
            var r = m.getReturnType().getSimpleName();
            var h = new RegisteredHandler(
                    ComponentType.Aggregate,
                    v.getRef().getClass().getSimpleName(),
                    HandlerType.AggregateCommandHandler,
                    PayloadType.DomainCommand,
                    k, r, false, null
            );
            applyComponentMetadata(h, v.getRef().getClass());
            applyInvocations(h, m);
            handlers.add(h);
            var esh = v.getEventSourcingHandler(r);
            if (esh != null) {
                var esHandler = new RegisteredHandler(
                        ComponentType.Aggregate,
                        v.getRef().getClass().getSimpleName(),
                        HandlerType.EventSourcingHandler,
                        PayloadType.DomainEvent,
                        r, null, false, null
                );
                applyComponentMetadata(esHandler, v.getRef().getClass());
                applyInvocations(esHandler, esh);
                handlers.add(esHandler);
            }
            payloads.add(m.getParameterTypes()[0]);
            payloads.add(m.getReturnType());
        });

        serviceManager.getHandlers().forEach((k, v) -> {
            var m = v.getAggregateCommandHandler(k);
            var r = m.getReturnType().getSimpleName();
            var h = new RegisteredHandler(
                    ComponentType.Service,
                    v.getRef().getClass().getSimpleName(),
                    HandlerType.CommandHandler,
                    PayloadType.ServiceCommand,
                    k, r.equals("void") ? null : r, false, null
            );
            applyComponentMetadata(h, v.getRef().getClass());
            applyInvocations(h, m);
            handlers.add(h);
            payloads.add(m.getParameterTypes()[0]);
            payloads.add(m.getReturnType());
        });

        projectorManager.getHandlers().forEach((k, v) -> v.forEach((k1, v1) -> {
            var m = v1.getEventHandler(k);
            var h = new RegisteredHandler(
                    ComponentType.Projector,
                    v1.getRef().getClass().getSimpleName(),
                    HandlerType.EventHandler,
                    m.getParameterTypes()[0].getSuperclass()
                            .isAssignableFrom(DomainEvent.class)
                            ? PayloadType.DomainEvent : PayloadType.ServiceEvent,
                    k, null, false, null
            );
            applyComponentMetadata(h, v1.getRef().getClass());
            applyInvocations(h, m);
            handlers.add(h);
            payloads.add(m.getParameterTypes()[0]);
        }));

        observerManager.getHandlers().forEach((k, v) -> v.forEach((k1, v1) -> {
            var m = v1.getEventHandler(k);
            var h = new RegisteredHandler(
                    ComponentType.Observer,
                    v1.getRef().getClass().getSimpleName(),
                    HandlerType.EventHandler,
                    m.getParameterTypes()[0].getSuperclass()
                            .isAssignableFrom(DomainEvent.class)
                            ? PayloadType.DomainEvent : PayloadType.ServiceEvent,
                    k, null, false, null
            );
            applyComponentMetadata(h, v1.getRef().getClass());
            applyInvocations(h, m);
            handlers.add(h);
            payloads.add(m.getParameterTypes()[0]);
        }));

        sagaManager.getHandlers().forEach((k, v) -> v.forEach((k1, v1) -> {
            var m = v1.getSagaEventHandler(k);
            var h = new RegisteredHandler(
                    ComponentType.Saga,
                    v1.getRef().getClass().getSimpleName(),
                    HandlerType.SagaEventHandler,
                    m.getParameterTypes()[0].getSuperclass()
                            .isAssignableFrom(DomainEvent.class)
                            ? PayloadType.DomainEvent : PayloadType.ServiceEvent,
                    k, null, false,
                    m.getAnnotation(SagaEventHandler.class).associationProperty()
            );
            applyComponentMetadata(h, v1.getRef().getClass());
            applyInvocations(h, m);
            handlers.add(h);
            payloads.add(m.getParameterTypes()[0]);
        }));

        projectionManager.getHandlers().forEach((k, v) -> {
            var m = v.getQueryHandler(k);
            var r = m.getReturnType();
            var h = new RegisteredHandler(
                    ComponentType.Projection,
                    v.getRef().getClass().getSimpleName(),
                    HandlerType.QueryHandler,
                    PayloadType.Query,
                    k,
                    ((Class<?>) ((ParameterizedType) m.getGenericReturnType())
                            .getActualTypeArguments()[0]).getSimpleName(),
                    r.isAssignableFrom(Multiple.class),
                    null
            );
            applyComponentMetadata(h, v.getRef().getClass());
            applyInvocations(h, m);
            handlers.add(h);
            payloads.add(m.getParameterTypes()[0]);
            payloads.add((Class<?>) ((ParameterizedType) m.getGenericReturnType())
                    .getActualTypeArguments()[0]);
        });

        invokerManager.getHandlerMethods().forEach((h, m) -> {
            applyComponentMetadata(h, m.getDeclaringClass());
            applyInvocations(h, m);
        });
        handlers.addAll(invokerManager.getHandlers());

        ObjectMapper mapper = new ObjectMapper();
        JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(mapper);
        var payloadInfo = new HashMap<String, PayloadDiscoveryInfo>();
        for (Class<?> p : payloads) {
            if (p == null) continue;
            String schema = null;
            try {
                schema = mapper.writeValueAsString(schemaGen.generateSchema(p));
            } catch (Exception ignored) {}
            String domain = null;
            if (p.getAnnotation(Domain.class) != null) {
                domain = p.getAnnotation(Domain.class).name();
            }
            var pAnn = p.getAnnotation(EventoDescription.class);
            String pDesc  = (pAnn != null && !pAnn.value().isEmpty()) ? pAnn.value() : "";
            String pDetail = pAnn != null ? pAnn.detail() : "";
            var pMeta = AsmClassMetadataScanner.scan(p);
            payloadInfo.put(p.getSimpleName(),
                    new PayloadDiscoveryInfo(schema, domain, pDesc, pDetail,
                            pMeta.sourcePath(), pMeta.declarationLine()));
        }

        return new Result(handlers, payloadInfo);
    }
}
