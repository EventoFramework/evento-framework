package com.evento.server.service;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.parser.model.BundleDescription;
import com.evento.parser.model.component.*;
import com.evento.parser.model.handler.*;
import com.evento.parser.model.payload.MultipleResultQueryReturnType;
import com.evento.parser.model.payload.PayloadDescription;
import com.evento.server.domain.model.core.BucketType;
import com.evento.server.domain.model.core.Bundle;
import com.evento.server.domain.model.core.Handler;
import com.evento.server.domain.model.core.Payload;
import com.evento.server.domain.repository.core.*;
import com.evento.server.domain.repository.core.projection.BundleListProjection;
import com.evento.server.service.deploy.BundleDeployService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The BundleService class provides methods for managing bundles in the system.
 */
@Service
public class BundleService {

    private final BundleRepository bundleRepository;

    private final HandlerRepository handlerRepository;
    private final PayloadRepository payloadRepository;

    private final BundleDeployService bundleDeployService;
    private final ComponentRepository componentRepository;

    private final PlatformTransactionManager tm;
    private final ConsumerRepository consumerRepository;

    /**
     * The BundleService class is responsible for managing bundles in the system.
     */
    public BundleService(BundleRepository bundleRepository, HandlerRepository handlerRepository, PayloadRepository payloadRepository, BundleDeployService bundleDeployService,
                         ComponentRepository componentRepository, PlatformTransactionManager tm, ConsumerRepository consumerRepository) {
        this.bundleRepository = bundleRepository;
        this.handlerRepository = handlerRepository;
        this.payloadRepository = payloadRepository;
        this.bundleDeployService = bundleDeployService;
        this.componentRepository = componentRepository;
        this.tm = tm;
        this.consumerRepository = consumerRepository;
    }


    /**
     * Registers a bundle with the given bundle information.
     * Updates or creates a new bundle in the bundle repository,
     * deletes existing handlers and components associated with the bundle,
     * deletes payloads not registered in the bundle repository,
     * saves payload descriptions, components, and handlers associated with the bundle.
     *
     * @param bundleId                      the ID of the bundle
     * @param bundleDeploymentBucketType    the type of bucket for the bundle deployment
     * @param bundleDeploymentArtifactCoordinates    the artifact coordinates for the bundle deployment
     * @param jarOriginalName               the original name of the JAR file
     * @param bundleDescription             the description of the bundle
     */
    public synchronized void register(
            String bundleId,
            BucketType bundleDeploymentBucketType,
            String bundleDeploymentArtifactCoordinates,
            String jarOriginalName,
            BundleDescription bundleDescription) {
        AtomicBoolean isNew = new AtomicBoolean(false);

        final Bundle bundle;
        var t = tm.getTransaction(TransactionDefinition.withDefaults());

        try {
            bundle = bundleRepository.findById(bundleId).map(b -> {
                b.setVersion(bundleDescription.getBundleVersion());
                b.setBucketType(bundleDeploymentBucketType);
                b.setArtifactCoordinates(bundleDeploymentArtifactCoordinates);
                b.setArtifactOriginalName(jarOriginalName);
                b.setContainsHandlers(!bundleDescription.getComponents().isEmpty());
                b.setAutorun(bundleDescription.getAutorun());
                b.setMinInstances(bundleDescription.getMinInstances());
                b.setMaxInstances(bundleDescription.getMaxInstances());
                b.setDescription(bundleDescription.getDescription());
                b.setDetail(bundleDescription.getDetail());
                b.setLinePrefix(bundleDescription.getLinePrefix());
                b.setDeployable(bundleDescription.getDeployable());
                b.setUpdatedAt(Instant.now());
                return bundleRepository.save(b);
            }).orElseGet(() -> {
                isNew.set(true);
                return bundleRepository.save(new Bundle(
                        bundleId,
                        bundleDescription.getBundleVersion(),
                        bundleDescription.getDescription(),
                        bundleDescription.getDetail(),
                        bundleDescription.getLinePrefix(),
                        bundleDeploymentBucketType,
                        bundleDeploymentArtifactCoordinates,
                        jarOriginalName,
                        !bundleDescription.getComponents().isEmpty(),
                        new HashMap<>(),
                        new HashMap<>(),
                        bundleDescription.getAutorun(),
                        bundleDescription.getDeployable(),
                        bundleDescription.getMinInstances(),
                        bundleDescription.getMaxInstances(),
                        Instant.now()));
            });

            for (Handler handler : handlerRepository.findAll()) {
                if (!handler.getComponent().getBundle().getId().equals(bundleId)) continue;
                handlerRepository.delete(handler);
                handler.getHandledPayload().getHandlers().remove(handler);
            }

            componentRepository.deleteAll(componentRepository.findAllByBundle_Id(bundleId));

            for (Payload payload : payloadRepository.findAll()) {
                try {
                    if (!bundleRepository.existsById(payload.getRegisteredIn()))
                        payloadRepository.delete(payload);
                } catch (Exception ignored) {
                }
            }

            if (!isNew.get() && bundle.getVersion() > bundleDescription.getBundleVersion())
                throw new IllegalArgumentException("Bundle " + bundleId + " with version " + bundle.getVersion() + " exists!");

            for (PayloadDescription payloadDescription : bundleDescription.getPayloadDescriptions()) {
                var payload = payloadRepository.findById(payloadDescription.getName()).orElseGet(Payload::new);
                payload.setName(payloadDescription.getName());
                payload.setJsonSchema(payloadDescription.getSchema());
                payload.setType(PayloadType.valueOf(payloadDescription.getType()));
                payload.setUpdatedAt(Instant.now());
                payload.setRegisteredIn(bundle.getId());
                payload.setValidJsonSchema(false);
                payload.setDescription(payloadDescription.getDescription());
                payload.setDetail(payloadDescription.getDetail());
                payload.setPath(payloadDescription.getPath());
                payload.setLine(payloadDescription.getLine());
                payload.setDomain(payloadDescription.getDomain());
                payloadRepository.save(payload);
            }

            for (Component component : bundleDescription.getComponents()) {
                componentRepository.findById(component.getComponentName())
                        .ifPresent(c -> Assert.isTrue(c.getBundle().getId().equals(bundleId),
                                "Component Duplicated: The component %s is already registered in bundle %s"
                                        .formatted(component.getComponentName(), bundleId)));
                switch (component) {
                    case Aggregate a -> {
                        for (AggregateCommandHandler aggregateCommandHandler : a.getAggregateCommandHandlers()) {
                            var handler = new Handler();
                            handler.setLine(aggregateCommandHandler.getLine());
                            handler.setComponent(componentRepository.findById(component.getComponentName()).orElseGet(() -> {
                                var c = new com.evento.server.domain.model.core.Component();
                                c.setBundle(bundle);
                                c.setComponentName(component.getComponentName());
                                c.setComponentType(ComponentType.Aggregate);
                                c.setDescription(component.getDescription());
                                c.setDetail(component.getDetail());
                                c.setPath(component.getPath());
                                c.setLine(component.getLine());
                                c.setUpdatedAt(Instant.now());
                                return componentRepository.save(c);

                            }));

                            handler.setHandlerType(HandlerType.AggregateCommandHandler);
                            handler.setHandledPayload(
                                    payloadRepository.findById(aggregateCommandHandler.getPayload().getName())
                                            .map(p -> {
                                                if (p.getType() != PayloadType.DomainCommand) {
                                                    p.setType(PayloadType.DomainCommand);
                                                    return payloadRepository.save(p);
                                                }
                                                return p;
                                            })
                                            .orElseGet(
                                                    () -> {
                                                        var payload = new Payload();
                                                        payload.setName(aggregateCommandHandler.getPayload().getName());
                                                        payload.setJsonSchema("null");
                                                        payload.setType(PayloadType.DomainCommand);
                                                        payload.setUpdatedAt(Instant.now());
                                                        payload.setRegisteredIn(bundle.getId());
                                                        payload.setValidJsonSchema(false);
                                                        return payloadRepository.save(payload);
                                                    }
                                            ));
                            handler.setReturnIsMultiple(false);
                            handler.setReturnType(payloadRepository.findById(aggregateCommandHandler.getProducedEvent().getName())
                                    .map(p -> {
                                        if (p.getType() != PayloadType.DomainEvent) {
                                            p.setType(PayloadType.DomainEvent);
                                            return payloadRepository.save(p);
                                        }
                                        return p;
                                    })
                                    .orElseGet(
                                            () -> {
                                                var payload = new Payload();
                                                payload.setName(aggregateCommandHandler.getProducedEvent().getName());
                                                payload.setJsonSchema("null");
                                                payload.setType(PayloadType.DomainEvent);
                                                payload.setUpdatedAt(Instant.now());
                                                payload.setRegisteredIn(bundle.getId());
                                                payload.setValidJsonSchema(false);
                                                return payloadRepository.save(payload);
                                            }
                                    ));
                            var invocations = new HashMap<Integer, Payload>();
                            for (var command : aggregateCommandHandler.getCommandInvocations().entrySet()) {
                                invocations.put(
                                        command.getKey(),
                                        payloadRepository.findById(command.getValue().getName()).orElseGet(
                                                () -> {
                                                    var payload = new Payload();
                                                    payload.setName(command.getValue().getName());
                                                    payload.setJsonSchema("null");
                                                    payload.setType(PayloadType.Command);
                                                    payload.setUpdatedAt(Instant.now());
                                                    payload.setRegisteredIn(bundle.getId());
                                                    payload.setValidJsonSchema(false);
                                                    return payloadRepository.save(payload);
                                                }
                                        ));
                            }
                            for (var query : aggregateCommandHandler.getQueryInvocations().entrySet()) {
                                invocations.put(query.getKey(), payloadRepository.findById(query.getValue().getName()).orElseGet(
                                        () -> {
                                            var payload = new Payload();
                                            payload.setName(query.getValue().getName());
                                            payload.setJsonSchema("null");
                                            payload.setType(PayloadType.Query);
                                            payload.setUpdatedAt(Instant.now());
                                            payload.setRegisteredIn(bundle.getId());
                                            payload.setValidJsonSchema(false);
                                            return payloadRepository.save(payload);
                                        }
                                ));
                            }
                            handler.setInvocations(invocations);
                            handler.generateId();
                            handlerRepository.save(handler);
                        }
                        for (EventSourcingHandler eventSourcingHandler : a.getEventSourcingHandlers()) {
                            var handler = new Handler();
                            handler.setLine(eventSourcingHandler.getLine());
                            handler.setComponent(componentRepository.findById(component.getComponentName()).orElseGet(() -> {
                                var c = new com.evento.server.domain.model.core.Component();
                                c.setBundle(bundle);
                                c.setComponentName(component.getComponentName());
                                c.setComponentType(ComponentType.Aggregate);
                                c.setDescription(component.getDescription());
                                c.setDetail(component.getDetail());
                                c.setPath(component.getPath());
                                c.setLine(component.getLine());
                                c.setUpdatedAt(Instant.now());
                                return componentRepository.save(c);

                            }));
                            handler.setHandlerType(HandlerType.EventSourcingHandler);
                            handler.setHandledPayload(payloadRepository.findById(eventSourcingHandler.getPayload().getName())
                                    .map(p -> {
                                        if (p.getType() != PayloadType.DomainEvent) {
                                            p.setType(PayloadType.DomainEvent);
                                            return payloadRepository.save(p);
                                        }
                                        return p;
                                    })
                                    .orElseGet(
                                            () -> {
                                                var payload = new Payload();
                                                payload.setName(eventSourcingHandler.getPayload().getName());
                                                payload.setJsonSchema("null");
                                                payload.setType(PayloadType.DomainEvent);
                                                payload.setUpdatedAt(Instant.now());
                                                payload.setRegisteredIn(bundle.getId());
                                                payload.setValidJsonSchema(false);
                                                return payloadRepository.save(payload);
                                            }
                                    ));
                            handler.setReturnIsMultiple(false);
                            handler.setReturnType(null);
                            handler.setInvocations(new HashMap<>());
                            handler.generateId();
                            handlerRepository.save(handler);
                        }
                    }
                    case Saga s -> {
                        for (SagaEventHandler sagaEventHandler : s.getSagaEventHandlers()) {
                            var handler = new Handler();
                            handler.setLine(sagaEventHandler.getLine());
                            handler.setComponent(componentRepository.findById(component.getComponentName()).orElseGet(() -> {
                                var c = new com.evento.server.domain.model.core.Component();
                                c.setBundle(bundle);
                                c.setComponentName(component.getComponentName());
                                c.setComponentType(ComponentType.Saga);
                                c.setDescription(component.getDescription());
                                c.setDetail(component.getDetail());
                                c.setPath(component.getPath());
                                c.setLine(component.getLine());
                                c.setUpdatedAt(Instant.now());
                                return componentRepository.save(c);

                            }));
                            handler.setHandlerType(HandlerType.SagaEventHandler);
                            handler.setHandledPayload(payloadRepository.findById(sagaEventHandler.getPayload().getName()).orElseGet(
                                    () -> {
                                        var payload = new Payload();
                                        payload.setName(sagaEventHandler.getPayload().getName());
                                        payload.setJsonSchema("null");
                                        payload.setType(PayloadType.Event);
                                        payload.setUpdatedAt(Instant.now());
                                        payload.setRegisteredIn(bundle.getId());
                                        payload.setUpdatedAt(Instant.now());
                                        payload.setValidJsonSchema(false);
                                        return payloadRepository.save(payload);
                                    }
                            ));
                            handler.setReturnIsMultiple(false);
                            handler.setReturnType(null);
                            handler.setAssociationProperty(sagaEventHandler.getAssociationProperty());
                            var invocations = new HashMap<Integer, Payload>();
                            for (var command : sagaEventHandler.getCommandInvocations().entrySet()) {
                                invocations.put(
                                        command.getKey(),
                                        payloadRepository.findById(command.getValue().getName()).orElseGet(
                                                () -> {
                                                    var payload = new Payload();
                                                    payload.setName(command.getValue().getName());
                                                    payload.setJsonSchema("null");
                                                    payload.setType(PayloadType.Command);
                                                    payload.setUpdatedAt(Instant.now());
                                                    payload.setRegisteredIn(bundle.getId());
                                                    payload.setValidJsonSchema(false);
                                                    return payloadRepository.save(payload);
                                                }
                                        ));
                            }
                            for (var query : sagaEventHandler.getQueryInvocations().entrySet()) {
                                invocations.put(query.getKey(), payloadRepository.findById(query.getValue().getName()).orElseGet(
                                        () -> {
                                            var payload = new Payload();
                                            payload.setName(query.getValue().getName());
                                            payload.setJsonSchema("null");
                                            payload.setType(PayloadType.Query);
                                            payload.setUpdatedAt(Instant.now());
                                            payload.setRegisteredIn(bundle.getId());
                                            payload.setValidJsonSchema(false);
                                            return payloadRepository.save(payload);
                                        }
                                ));
                            }
                            handler.setInvocations(invocations);
                            handler.generateId();
                            handlerRepository.save(handler);
                        }
                    }
                    case Projection p -> {
                        for (QueryHandler queryHandler : p.getQueryHandlers()) {
                            var handler = new Handler();
                            handler.setLine(queryHandler.getLine());
                            handler.setComponent(componentRepository.findById(component.getComponentName()).orElseGet(() -> {
                                var c = new com.evento.server.domain.model.core.Component();
                                c.setBundle(bundle);
                                c.setComponentName(component.getComponentName());
                                c.setComponentType(ComponentType.Projection);
                                c.setDescription(component.getDescription());
                                c.setDetail(component.getDetail());
                                c.setPath(component.getPath());
                                c.setLine(component.getLine());
                                c.setUpdatedAt(Instant.now());
                                return componentRepository.save(c);

                            }));
                            handler.setHandlerType(HandlerType.QueryHandler);
                            handler.setHandledPayload(payloadRepository.findById(queryHandler.getPayload().getName()).orElseGet(
                                    () -> {
                                        var payload = new Payload();
                                        payload.setName(queryHandler.getPayload().getName());
                                        payload.setJsonSchema("null");
                                        payload.setType(PayloadType.Query);
                                        payload.setUpdatedAt(Instant.now());
                                        payload.setRegisteredIn(bundle.getId());
                                        payload.setValidJsonSchema(false);
                                        return payloadRepository.save(payload);
                                    }
                            ));
                            handler.setReturnIsMultiple(queryHandler.getPayload().getReturnType() instanceof MultipleResultQueryReturnType);
                            handler.setReturnType(payloadRepository.findById(queryHandler.getPayload().getReturnType().getViewName()).orElseGet(
                                    () -> {
                                        var payload = new Payload();
                                        payload.setName(queryHandler.getPayload().getReturnType().getViewName());
                                        payload.setJsonSchema("null");
                                        payload.setType(PayloadType.View);
                                        payload.setUpdatedAt(Instant.now());
                                        payload.setRegisteredIn(bundle.getId());
                                        payload.setValidJsonSchema(false);
                                        return payloadRepository.save(payload);
                                    }
                            ));
                            var invocations = new HashMap<Integer, Payload>();
                            for (var query : queryHandler.getQueryInvocations().entrySet()) {
                                invocations.put(query.getKey(), payloadRepository.findById(query.getValue().getName()).orElseGet(
                                        () -> {
                                            var payload = new Payload();
                                            payload.setName(query.getValue().getName());
                                            payload.setJsonSchema("null");
                                            payload.setType(PayloadType.Query);
                                            payload.setUpdatedAt(Instant.now());
                                            payload.setRegisteredIn(bundle.getId());
                                            payload.setValidJsonSchema(false);
                                            return payloadRepository.save(payload);
                                        }
                                ));
                            }
                            handler.setInvocations(invocations);
                            handler.generateId();
                            handlerRepository.save(handler);

                        }
                    }
                    case Projector p -> {
                        for (EventHandler eventHandler : p.getEventHandlers()) {
                            var handler = new Handler();
                            handler.setLine(eventHandler.getLine());
                            handler.setComponent(componentRepository.findById(component.getComponentName()).orElseGet(() -> {
                                var c = new com.evento.server.domain.model.core.Component();
                                c.setBundle(bundle);
                                c.setComponentName(component.getComponentName());
                                c.setComponentType(ComponentType.Projector);
                                c.setDescription(component.getDescription());
                                c.setDetail(component.getDetail());
                                c.setPath(component.getPath());
                                c.setLine(component.getLine());
                                c.setUpdatedAt(Instant.now());
                                return componentRepository.save(c);

                            }));
                            handler.setHandlerType(HandlerType.EventHandler);
                            handler.setHandledPayload(payloadRepository.findById(eventHandler.getPayload().getName()).orElseGet(
                                    () -> {
                                        var payload = new Payload();
                                        payload.setName(eventHandler.getPayload().getName());
                                        payload.setJsonSchema("null");
                                        payload.setType(PayloadType.Event);
                                        payload.setUpdatedAt(Instant.now());
                                        payload.setRegisteredIn(bundle.getId());
                                        payload.setValidJsonSchema(false);
                                        return payloadRepository.save(payload);
                                    }
                            ));
                            handler.setReturnIsMultiple(false);
                            handler.setReturnType(null);
                            var invocations = new HashMap<Integer, Payload>();
                            for (var query : eventHandler.getQueryInvocations().entrySet()) {
                                invocations.put(query.getKey(), payloadRepository.findById(query.getValue().getName()).orElseGet(
                                        () -> {
                                            var payload = new Payload();
                                            payload.setName(query.getValue().getName());
                                            payload.setJsonSchema("null");
                                            payload.setType(PayloadType.Query);
                                            payload.setUpdatedAt(Instant.now());
                                            payload.setRegisteredIn(bundle.getId());
                                            payload.setValidJsonSchema(false);
                                            return payloadRepository.save(payload);
                                        }
                                ));
                            }
                            handler.setInvocations(invocations);
                            handler.generateId();
                            handlerRepository.save(handler);

                        }
                    }
                    case Observer o -> {
                        for (EventHandler eventHandler : o.getEventHandlers()) {
                            var handler = new Handler();
                            handler.setLine(eventHandler.getLine());
                            handler.setComponent(componentRepository.findById(component.getComponentName()).orElseGet(() -> {
                                var c = new com.evento.server.domain.model.core.Component();
                                c.setBundle(bundle);
                                c.setComponentName(component.getComponentName());
                                c.setComponentType(ComponentType.Observer);
                                c.setDescription(component.getDescription());
                                c.setDetail(component.getDetail());
                                c.setPath(component.getPath());
                                c.setLine(component.getLine());
                                c.setUpdatedAt(Instant.now());
                                return componentRepository.save(c);

                            }));
                            handler.setHandlerType(HandlerType.EventHandler);
                            handler.setHandledPayload(payloadRepository.findById(eventHandler.getPayload().getName()).orElseGet(
                                    () -> {
                                        var payload = new Payload();
                                        payload.setName(eventHandler.getPayload().getName());
                                        payload.setJsonSchema("null");
                                        payload.setType(PayloadType.Event);
                                        payload.setUpdatedAt(Instant.now());
                                        payload.setRegisteredIn(bundle.getId());
                                        payload.setValidJsonSchema(false);
                                        return payloadRepository.save(payload);
                                    }
                            ));
                            handler.setReturnIsMultiple(false);
                            handler.setReturnType(null);
                            var invocations = new HashMap<Integer, Payload>();
                            for (var query : eventHandler.getQueryInvocations().entrySet()) {
                                invocations.put(query.getKey(), payloadRepository.findById(query.getValue().getName()).orElseGet(
                                        () -> {
                                            var payload = new Payload();
                                            payload.setName(query.getValue().getName());
                                            payload.setJsonSchema("null");
                                            payload.setType(PayloadType.Query);
                                            payload.setUpdatedAt(Instant.now());
                                            payload.setRegisteredIn(bundle.getId());
                                            payload.setValidJsonSchema(false);
                                            return payloadRepository.save(payload);
                                        }
                                ));
                            }
                            for (var command : eventHandler.getCommandInvocations().entrySet()) {
                                invocations.put(command.getKey(), payloadRepository.findById(command.getValue().getName()).orElseGet(
                                        () -> {
                                            var payload = new Payload();
                                            payload.setName(command.getValue().getName());
                                            payload.setJsonSchema("null");
                                            payload.setType(PayloadType.Command);
                                            payload.setUpdatedAt(Instant.now());
                                            payload.setRegisteredIn(bundle.getId());
                                            payload.setValidJsonSchema(false);
                                            return payloadRepository.save(payload);
                                        }
                                ));
                            }
                            handler.setInvocations(invocations);
                            handler.generateId();
                            handlerRepository.save(handler);

                        }
                    }
                    case com.evento.parser.model.component.Service s -> {
                        for (ServiceCommandHandler commandHandler : s.getCommandHandlers()) {
                            var handler = new Handler();
                            handler.setLine(commandHandler.getLine());
                            handler.setComponent(componentRepository.findById(component.getComponentName()).orElseGet(() -> {
                                var c = new com.evento.server.domain.model.core.Component();
                                c.setBundle(bundle);
                                c.setComponentName(component.getComponentName());
                                c.setComponentType(ComponentType.Service);
                                c.setDescription(component.getDescription());
                                c.setDetail(component.getDetail());
                                c.setPath(component.getPath());
                                c.setLine(component.getLine());
                                c.setUpdatedAt(Instant.now());
                                return componentRepository.save(c);

                            }));
                            handler.setHandlerType(HandlerType.CommandHandler);
                            handler.setHandledPayload(payloadRepository.findById(commandHandler.getPayload().getName())
                                    .map(p -> {
                                        if (p.getType() != PayloadType.ServiceCommand) {
                                            p.setType(PayloadType.ServiceCommand);
                                            return payloadRepository.save(p);
                                        }
                                        return p;
                                    })
                                    .orElseGet(
                                            () -> {
                                                var payload = new Payload();
                                                payload.setName(commandHandler.getPayload().getName());
                                                payload.setJsonSchema("null");
                                                payload.setType(PayloadType.ServiceCommand);
                                                payload.setUpdatedAt(Instant.now());
                                                payload.setRegisteredIn(bundle.getId());
                                                payload.setValidJsonSchema(false);
                                                return payloadRepository.save(payload);
                                            }
                                    ));
                            handler.setReturnIsMultiple(false);
                            handler.setReturnType(commandHandler.getProducedEvent() == null ? null : payloadRepository.findById(commandHandler.getProducedEvent().getName())
                                    .map(p -> {
                                        if (p.getType() != PayloadType.ServiceEvent) {
                                            p.setType(PayloadType.ServiceEvent);
                                            return payloadRepository.save(p);
                                        }
                                        return p;
                                    })
                                    .orElseGet(
                                            () -> {
                                                var payload = new Payload();
                                                payload.setName(commandHandler.getProducedEvent().getName());
                                                payload.setJsonSchema("null");
                                                payload.setType(PayloadType.ServiceEvent);
                                                payload.setUpdatedAt(Instant.now());
                                                payload.setRegisteredIn(bundle.getId());
                                                payload.setValidJsonSchema(false);
                                                return payloadRepository.save(payload);
                                            }
                                    ));
                            var invocations = new HashMap<Integer, Payload>();
                            for (var query : commandHandler.getQueryInvocations().entrySet()) {
                                invocations.put(query.getKey(), payloadRepository.findById(query.getValue().getName()).orElseGet(
                                        () -> {
                                            var payload = new Payload();
                                            payload.setName(query.getValue().getName());
                                            payload.setJsonSchema("null");
                                            payload.setType(PayloadType.Query);
                                            payload.setUpdatedAt(Instant.now());
                                            payload.setRegisteredIn(bundle.getId());
                                            payload.setValidJsonSchema(false);
                                            return payloadRepository.save(payload);
                                        }
                                ));
                            }
                            for (var command : commandHandler.getCommandInvocations().entrySet()) {
                                invocations.put(command.getKey(), payloadRepository.findById(command.getValue().getName()).orElseGet(
                                        () -> {
                                            var payload = new Payload();
                                            payload.setName(command.getValue().getName());
                                            payload.setJsonSchema("null");
                                            payload.setType(PayloadType.Command);
                                            payload.setUpdatedAt(Instant.now());
                                            payload.setRegisteredIn(bundle.getId());
                                            payload.setValidJsonSchema(false);
                                            return payloadRepository.save(payload);
                                        }
                                ));
                            }
                            handler.setInvocations(invocations);
                            handler.generateId();
                            handlerRepository.save(handler);
                        }
                    }
                    case Invoker i -> {
                        for (InvocationHandler invocationHandler : i.getInvocationHandlers()) {
                            var handler = new Handler();
                            handler.setLine(invocationHandler.getLine());
                            handler.setComponent(componentRepository.findById(component.getComponentName()).orElseGet(() -> {
                                var c = new com.evento.server.domain.model.core.Component();
                                c.setBundle(bundle);
                                c.setComponentName(component.getComponentName());
                                c.setComponentType(ComponentType.Invoker);
                                c.setDescription(component.getDescription());
                                c.setDetail(component.getDetail());
                                c.setPath(component.getPath());
                                c.setLine(component.getLine());
                                c.setUpdatedAt(Instant.now());
                                return componentRepository.save(c);

                            }));
                            handler.setHandlerType(HandlerType.InvocationHandler);
                            handler.setHandledPayload(payloadRepository.getReferenceById(invocationHandler.getPayload().getName()));
                            handler.setReturnIsMultiple(false);
                            handler.setReturnType(null);
                            var invocations = new HashMap<Integer, Payload>();
                            for (var query : invocationHandler.getQueryInvocations().entrySet()) {
                                invocations.put(query.getKey(), payloadRepository.findById(query.getValue().getName()).orElseGet(
                                        () -> {
                                            var payload = new Payload();
                                            payload.setName(query.getValue().getName());
                                            payload.setJsonSchema("null");
                                            payload.setType(PayloadType.Query);
                                            payload.setUpdatedAt(Instant.now());
                                            payload.setRegisteredIn(bundle.getId());
                                            payload.setValidJsonSchema(false);
                                            return payloadRepository.save(payload);
                                        }
                                ));
                            }
                            for (var command : invocationHandler.getCommandInvocations().entrySet()) {
                                invocations.put(
                                        command.getKey(),
                                        payloadRepository.findById(command.getValue().getName()).orElseGet(
                                                () -> {
                                                    var payload = new Payload();
                                                    payload.setName(command.getValue().getName());
                                                    payload.setJsonSchema("null");
                                                    payload.setType(PayloadType.Command);
                                                    payload.setUpdatedAt(Instant.now());
                                                    payload.setRegisteredIn(bundle.getId());
                                                    payload.setValidJsonSchema(false);
                                                    return payloadRepository.save(payload);
                                                }
                                        ));
                            }
                            handler.setInvocations(invocations);
                            handler.generateId();
                            handlerRepository.save(handler);
                        }
                    }
                    default -> {
                    }
                }
            }

            tm.commit(t);
        } catch (Exception e) {
            tm.rollback(t);
            throw e;
        }


        if (bundle.isDeployable() && bundle.isAutorun() && bundle.getBucketType() != BucketType.Ephemeral) {
            try {
                bundleDeployService.spawn(bundle);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    /**
     * Unregisters a bundle and deletes all associated handlers, components, and payloads.
     *
     * @param bundleId the ID of the bundle to unregister
     */
    public void unregister(
            String bundleId) {
        for (Handler handler : handlerRepository.findAll()) {
            if (!handler.getComponent().getBundle().getId().equals(bundleId)) continue;
            handlerRepository.delete(handler);
            handler.getHandledPayload().getHandlers().remove(handler);
        }
        var components = componentRepository.findAllByBundle_Id(bundleId);
        consumerRepository.deleteAllByComponentIn(components);

        componentRepository.deleteAll(components);

        bundleRepository.findById(bundleId).ifPresent(bundleRepository::delete);
        for (Payload payload : payloadRepository.findAll()) {
            try {
                if (!bundleRepository.existsById(payload.getRegisteredIn()))
                    payloadRepository.delete(payload);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Retrieves a list of all bundles in the system.
     *
     * @return A list of bundles.
     */
    public List<Bundle> findAllBundles() {
        return bundleRepository.findAll();
    }

    /**
     * Retrieves a list of BundleListProjection objects representing a projected view of Bundle objects.
     * <p>
     * The findAllProjection method executes a native SQL query to fetch the required data from the database.
     *
     * @return A list of BundleListProjection objects representing the projected view of Bundle objects.
     *
     * @see BundleListProjection
     */
    public List<BundleListProjection> findAllProjection() {
        return bundleRepository.findAllProjection();
    }

    /**
     * Finds a bundle by its ID.
     *
     * @param bundleId the ID of the bundle to find
     * @return the Bundle object representing the found bundle
     * @throws java.util.NoSuchElementException if no bundle with the given ID is found
     */
    public Bundle findById(String bundleId) {
        return bundleRepository.findById(bundleId).orElseThrow();
    }

    /**
     * Updates the value of an environment variable for a given bundle.
     * <p>
     * This method updates the value of an environment variable associated with a specific bundle.
     * The updated value is saved in the bundle's environment map, and the bundle's updatedAt timestamp is updated.
     *
     * @param bundleId the ID of the bundle to update the environment variable for
     * @param key the key of the environment variable
     * @param value the new value of the environment variable
     */
    public void putEnv(String bundleId, String key, String value) {
        var bundle = bundleRepository.findById(bundleId).orElseThrow();
        bundle.getEnvironment().put(key, value);
        bundle.setUpdatedAt(Instant.now());
        bundleRepository.save(bundle);
    }

    /**
     * Removes an environment variable for a given bundle.
     * <p>
     * This method removes the specified environment variable for the bundle with the provided ID.
     * The bundle is retrieved from the bundle repository using the ID.
     * If no bundle is found with the provided ID, a NoSuchElementException is thrown.
     * The environment variable is then removed from the bundle's environment map.
     * The bundle's updatedAt property is updated with the current timestamp.
     * Finally, the updated bundle is saved in the bundle repository.
     *
     * @param bundleId the ID of the bundle to remove the environment variable for
     * @param key the key of the environment variable to remove
     */
    public void removeEnv(String bundleId, String key) {
        var bundle = bundleRepository.findById(bundleId).orElseThrow();
        bundle.getEnvironment().remove(key);
        bundle.setUpdatedAt(Instant.now());
        bundleRepository.save(bundle);
    }

    /**
     * Updates the virtual machine option of a given bundle with the provided key-value pair.
     *
     * @param bundleId the ID of the bundle to update
     * @param key the key of the virtual machine option to update
     * @param value the value of the virtual machine option to update
     * @throws NoSuchElementException if the bundle with the provided ID does not exist
     */
    public void putVmOption(String bundleId, String key, String value) {
        var bundle = bundleRepository.findById(bundleId).orElseThrow();
        bundle.getVmOptions().put(key, value);
        bundle.setUpdatedAt(Instant.now());
        bundleRepository.save(bundle);
    }

    /**
     * Removes a specific VM option from a bundle identified by bundleId.
     *
     * @param bundleId the identifier of the bundle to remove the VM option from
     * @param key the key of the VM option to be removed
     */
    public void removeVmOption(String bundleId, String key) {
        var bundle = bundleRepository.findById(bundleId).orElseThrow();
        bundle.getVmOptions().remove(key);
        bundle.setUpdatedAt(Instant.now());
        bundleRepository.save(bundle);
    }
}
