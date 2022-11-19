package org.eventrails.server.service;

import org.eventrails.parser.model.BundleDescription;
import org.eventrails.parser.model.handler.*;
import org.eventrails.parser.model.component.*;
import org.eventrails.parser.model.payload.Command;
import org.eventrails.parser.model.payload.MultipleResultQueryReturnType;
import org.eventrails.parser.model.payload.PayloadDescription;
import org.eventrails.parser.model.payload.Query;
import org.eventrails.server.domain.model.*;
import org.eventrails.server.domain.model.Handler;
import org.eventrails.common.modeling.bundle.types.ComponentType;
import org.eventrails.common.modeling.bundle.types.HandlerType;
import org.eventrails.common.modeling.bundle.types.PayloadType;
import org.eventrails.server.domain.repository.BundleRepository;
import org.eventrails.server.domain.repository.HandlerRepository;
import org.eventrails.server.domain.repository.PayloadRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

@Service
public class BundleService {

    private final BundleRepository bundleRepository;

    private final HandlerRepository handlerRepository;
    private final PayloadRepository payloadRepository;

    public BundleService(BundleRepository bundleRepository, HandlerRepository handlerRepository, PayloadRepository payloadRepository) {
        this.bundleRepository = bundleRepository;
        this.handlerRepository = handlerRepository;
        this.payloadRepository = payloadRepository;
    }


    public void register(
            String bundleDeploymentName,
            BucketType bundleDeploymentBucketType,
            String bundleDeploymentArtifactCoordinates,
            String jarOriginalName,
            BundleDescription bundleDescription) {
        Bundle bundle = bundleRepository.save(new Bundle(
                bundleDeploymentName,
                bundleDeploymentBucketType,
                bundleDeploymentArtifactCoordinates,
                jarOriginalName,
                bundleDescription.getComponents().size() > 0,
                new HashMap<>(),
                new HashMap<>()));
        for (PayloadDescription payloadDescription : bundleDescription.getPayloadDescriptions()) {
            var payload = new Payload();
            payload.setName(payloadDescription.getName());
            payload.setJsonSchema(payloadDescription.getSchema().toString());
            payload.setType(PayloadType.valueOf(payloadDescription.getType()));
            payload.setUpdatedAt(Instant.now());
            payload.setRegisteredIn(bundle.getName());
            payloadRepository.save(payload);
        }
        for (Component component : bundleDescription.getComponents()) {
            if (component instanceof Aggregate a) {
                for (AggregateCommandHandler aggregateCommandHandler : a.getAggregateCommandHandlers()) {
                    var handler = new Handler();
                    handler.setBundle(bundle);
                    handler.setComponentName(component.getComponentName());
                    handler.setHandlerType(HandlerType.AggregateCommandHandler);
                    handler.setComponentType(ComponentType.Aggregate);
                    handler.setHandledPayload(
                            payloadRepository.findById(aggregateCommandHandler.getPayload().getName())
                                    .map(p -> {
                                        if(p.getType() != PayloadType.DomainCommand){
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
                                        payload.setRegisteredIn(bundle.getName());
                                        return payloadRepository.save(payload);
                                    }
                            ));
                    handler.setReturnIsMultiple(false);
                    handler.setReturnType( payloadRepository.findById(aggregateCommandHandler.getProducedEvent().getName())
                            .map(p -> {
                                if(p.getType() != PayloadType.DomainEvent){
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
                                payload.setRegisteredIn(bundle.getName());
                                return payloadRepository.save(payload);
                            }
                    ));
                    handler.setInvocations(new HashSet<>());
                    handler.generateId();
                    handlerRepository.save(handler);
                }
                for (EventSourcingHandler eventSourcingHandler : a.getEventSourcingHandlers()) {
                    var handler = new Handler();
                    handler.setBundle(bundle);
                    handler.setComponentName(component.getComponentName());
                    handler.setHandlerType(HandlerType.EventSourcingHandler);
                    handler.setComponentType(ComponentType.Aggregate);
                    handler.setHandledPayload( payloadRepository.findById(eventSourcingHandler.getPayload().getName())
                            .map(p -> {
                                if(p.getType() != PayloadType.DomainEvent){
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
                                payload.setRegisteredIn(bundle.getName());
                                return payloadRepository.save(payload);
                            }
                    ));
                    handler.setReturnIsMultiple(false);
                    handler.setReturnType(null);
                    handler.setInvocations(new HashSet<>());
                    handler.generateId();
                    handlerRepository.save(handler);
                }
            } else if (component instanceof Saga s) {
                for (SagaEventHandler sagaEventHandler : s.getSagaEventHandlers()) {
                    var handler = new Handler();
                    handler.setBundle(bundle);
                    handler.setComponentName(component.getComponentName());
                    handler.setHandlerType(HandlerType.SagaEventHandler);
                    handler.setComponentType(ComponentType.Saga);
                    handler.setHandledPayload(payloadRepository.findById(sagaEventHandler.getPayload().getName()).orElseGet(
                            () -> {
                                var payload = new Payload();
                                payload.setName(sagaEventHandler.getPayload().getName());
                                payload.setJsonSchema("null");
                                payload.setType(PayloadType.Event);
                                payload.setUpdatedAt(Instant.now());
                                payload.setRegisteredIn(bundle.getName());
                                return payloadRepository.save(payload);
                            }
                    ));
                    handler.setReturnIsMultiple(false);
                    handler.setReturnType(null);
                    handler.setAssociationProperty(sagaEventHandler.getAssociationProperty());
                    var invocations = new HashSet<Payload>();
                    for (Command command : sagaEventHandler.getCommandInvocations()) {
                        invocations.add(payloadRepository.findById(command.getName()).orElseGet(
                                () -> {
                                    var payload = new Payload();
                                    payload.setName(command.getName());
                                    payload.setJsonSchema("null");
                                    payload.setType(PayloadType.Command);
                                    payload.setUpdatedAt(Instant.now());
                                    payload.setRegisteredIn(bundle.getName());
                                    return payloadRepository.save(payload);
                                }
                        ));
                    }
                    for (Query query : sagaEventHandler.getQueryInvocations()) {
                        invocations.add(payloadRepository.findById(query.getName()).orElseGet(
                                () -> {
                                    var payload = new Payload();
                                    payload.setName(query.getName());
                                    payload.setJsonSchema("null");
                                    payload.setType(PayloadType.Query);
                                    payload.setUpdatedAt(Instant.now());
                                    payload.setRegisteredIn(bundle.getName());
                                    return payloadRepository.save(payload);
                                }
                        ));
                    }
                    handler.setInvocations(invocations);
                    handler.generateId();
                    handlerRepository.save(handler);
                }
            } else if (component instanceof Projection p) {
                for (QueryHandler queryHandler : p.getQueryHandlers()) {
                    var handler = new Handler();
                    handler.setBundle(bundle);
                    handler.setComponentName(component.getComponentName());
                    handler.setHandlerType(HandlerType.QueryHandler);
                    handler.setComponentType(ComponentType.Projection);
                    handler.setHandledPayload(payloadRepository.findById(queryHandler.getPayload().getName()).orElseGet(
                            () -> {
                                var payload = new Payload();
                                payload.setName(queryHandler.getPayload().getName());
                                payload.setJsonSchema("null");
                                payload.setType(PayloadType.Query);
                                payload.setUpdatedAt(Instant.now());
                                payload.setRegisteredIn(bundle.getName());
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
                                payload.setRegisteredIn(bundle.getName());
                                return payloadRepository.save(payload);
                            }
                    ));
                    handler.setInvocations(new HashSet<>());
                    handler.generateId();
                    handlerRepository.save(handler);

                }
            } else if (component instanceof Projector p) {
                for (EventHandler eventHandler : p.getEventHandlers()) {
                    var handler = new Handler();
                    handler.setBundle(bundle);
                    handler.setComponentName(component.getComponentName());
                    handler.setHandlerType(HandlerType.EventHandler);
                    handler.setComponentType(ComponentType.Projector);
                    handler.setHandledPayload(payloadRepository.findById(eventHandler.getPayload().getName()).orElseGet(
                            () -> {
                                var payload = new Payload();
                                payload.setName(eventHandler.getPayload().getName());
                                payload.setJsonSchema("null");
                                payload.setType(PayloadType.Event);
                                payload.setUpdatedAt(Instant.now());
                                payload.setRegisteredIn(bundle.getName());
                                return payloadRepository.save(payload);
                            }
                    ));
                    handler.setReturnIsMultiple(false);
                    handler.setReturnType(null);
                    var invocations = new HashSet<Payload>();
                    for (Query query : eventHandler.getQueryInvocations()) {
                        invocations.add(payloadRepository.findById(query.getName()).orElseGet(
                                () -> {
                                    var payload = new Payload();
                                    payload.setName(query.getName());
                                    payload.setJsonSchema("null");
                                    payload.setType(PayloadType.Query);
                                    payload.setUpdatedAt(Instant.now());
                                    payload.setRegisteredIn(bundle.getName());
                                    return payloadRepository.save(payload);
                                }
                        ));
                    }
                    handler.setInvocations(invocations);
                    handler.generateId();
                    handlerRepository.save(handler);

                }
            } else if (component instanceof org.eventrails.parser.model.component.Service s) {
                for (ServiceCommandHandler commandHandler : s.getCommandHandlers()) {
                    var handler = new Handler();
                    handler.setBundle(bundle);
                    handler.setComponentName(component.getComponentName());
                    handler.setHandlerType(HandlerType.CommandHandler);
                    handler.setComponentType(ComponentType.Service);
                    handler.setHandledPayload(payloadRepository.findById(commandHandler.getPayload().getName())
                            .map(p -> {
                                if(p.getType() != PayloadType.ServiceCommand){
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
                                payload.setRegisteredIn(bundle.getName());
                                return payloadRepository.save(payload);
                            }
                    ));
                    handler.setReturnIsMultiple(false);
                    handler.setReturnType(commandHandler.getProducedEvent() == null ? null : payloadRepository.findById(commandHandler.getProducedEvent().getName())
                                    .map(p -> {
                                        if(p.getType() != PayloadType.ServiceEvent){
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
                                payload.setRegisteredIn(bundle.getName());
                                return payloadRepository.save(payload);
                            }
                    ));
                    var invocations = new HashSet<Payload>();
                    for (Query query : commandHandler.getQueryInvocations()) {
                        invocations.add(payloadRepository.findById(query.getName()).orElseGet(
                                () -> {
                                    var payload = new Payload();
                                    payload.setName(query.getName());
                                    payload.setJsonSchema("null");
                                    payload.setType(PayloadType.Query);
                                    payload.setUpdatedAt(Instant.now());
                                    payload.setRegisteredIn(bundle.getName());
                                    return payloadRepository.save(payload);
                                }
                        ));
                    }
                    for (Command command : commandHandler.getCommandInvocations()) {
                        invocations.add(payloadRepository.findById(command.getName()).orElseGet(
                                () -> {
                                    var payload = new Payload();
                                    payload.setName(command.getName());
                                    payload.setJsonSchema("null");
                                    payload.setType(PayloadType.Command);
                                    payload.setUpdatedAt(Instant.now());
                                    payload.setRegisteredIn(bundle.getName());
                                    return payloadRepository.save(payload);
                                }
                        ));
                    }
                    handler.setInvocations(invocations);
                    handler.generateId();
                    handlerRepository.save(handler);
                }
            } else if (component instanceof Invoker i) {
                for (InvocationHandler invocationHandler : i.getInvocationHandlers()) {
                    var handler = new Handler();
                    handler.setBundle(bundle);
                    handler.setComponentName(component.getComponentName());
                    handler.setHandlerType(HandlerType.InvocationHandler);
                    handler.setComponentType(ComponentType.Invoker);
                    handler.setHandledPayload(payloadRepository.getById(invocationHandler.getPayload().getName()));
                    handler.setReturnIsMultiple(false);
                    handler.setReturnType(null);
                    var invocations = new HashSet<Payload>();
                    for (Query query : invocationHandler.getQueryInvocations()) {
                        invocations.add(payloadRepository.getById(query.getName()));
                    }
                    for (Command command : invocationHandler.getCommandInvocations()) {
                        invocations.add(payloadRepository.getById(command.getName()));
                    }
                    handler.setInvocations(invocations);
                    handler.generateId();
                    handlerRepository.save(handler);
                }
            }
        }
    }

    public void unregister(
            String bundleDeploymentName) {
        for (Handler handler : handlerRepository.findAll()) {
            if (!handler.getBundle().getName().equals(bundleDeploymentName)) continue;
            handlerRepository.delete(handler);
            handler.getHandledPayload().getHandlers().remove(handler);
        }

        bundleRepository.findById(bundleDeploymentName).ifPresent(bundleRepository::delete);
        for (Payload payload : payloadRepository.findAll()) {
            try {
                if(!bundleRepository.existsById(payload.getRegisteredIn()))
                    payloadRepository.delete(payload);
            } catch (Exception ignored) {
            }
        }
    }

    public List<Bundle> findAllBundles() {
        return bundleRepository.findAll();
    }

    public Bundle findByName(String bundleName) {
        return bundleRepository.findById(bundleName).orElseThrow();
    }

    public void putEnv(String bundleName, String key, String value) {
        var bundle = bundleRepository.findById(bundleName).orElseThrow();
        bundle.getEnvironment().put(key, value);
        bundleRepository.save(bundle);
    }

    public void removeEnv(String bundleName, String key) {
        var bundle = bundleRepository.findById(bundleName).orElseThrow();
        bundle.getEnvironment().remove(key);
        bundleRepository.save(bundle);
    }

    public void putVmOption(String bundleName, String key, String value) {
        var bundle = bundleRepository.findById(bundleName).orElseThrow();
        bundle.getVmOptions().put(key, value);
        bundleRepository.save(bundle);
    }

    public void removeVmOption(String bundleName, String key) {
        var bundle = bundleRepository.findById(bundleName).orElseThrow();
        bundle.getVmOptions().remove(key);
        bundleRepository.save(bundle);
    }
}
