package org.evento.server.service.discovery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.modeling.bundle.types.PayloadType;
import org.evento.common.modeling.messaging.message.internal.discovery.BundleRegistration;
import org.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import org.evento.server.bus.MessageBus;
import org.evento.server.bus.NodeAddress;
import org.evento.server.domain.model.core.BucketType;
import org.evento.server.domain.model.core.Bundle;
import org.evento.server.domain.model.core.Handler;
import org.evento.server.domain.model.core.Payload;
import org.evento.server.domain.model.core.Component;
import org.evento.server.domain.repository.core.BundleRepository;
import org.evento.server.domain.repository.core.ComponentRepository;
import org.evento.server.domain.repository.core.HandlerRepository;
import org.evento.server.domain.repository.core.PayloadRepository;
import org.evento.server.service.BundleService;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;

/**
 * Service class for auto-discovery of bundles, handlers, and payloads.
 */
@Service
public class AutoDiscoveryService {
    private final Logger logger = LogManager.getLogger(AutoDiscoveryService.class);
    private final BundleRepository bundleRepository;
    private final HandlerRepository handlerRepository;
    private final PayloadRepository payloadRepository;
    private final BundleService bundleService;
    private final LockRegistry lockRegistry;
    private final ComponentRepository componentRepository;

    /**
     * Service responsible for auto-discovery of components in the system.
     *
     * @param messageBus       The message bus used for communication between components.
     * @param bundleRepository The repository for managing bundles of components.
     * @param handlerRepository The repository for managing component handlers.
     * @param payloadRepository The repository for managing component payloads.
     * @param bundleService    The service for managing bundles of components.
     * @param lockRegistry     The registry for managing locks.
     * @param componentRepository The repository for managing components.
     */
    public AutoDiscoveryService(MessageBus messageBus,
                                BundleRepository bundleRepository,
                                HandlerRepository handlerRepository,
                                PayloadRepository payloadRepository, BundleService bundleService, LockRegistry lockRegistry,
                                ComponentRepository componentRepository) {
        this.bundleRepository = bundleRepository;
        this.handlerRepository = handlerRepository;
        this.payloadRepository = payloadRepository;
        this.bundleService = bundleService;
        this.lockRegistry = lockRegistry;
        messageBus.addJoinListener(this::onNodeJoin);
        messageBus.addLeaveListener(this::onNodeLeave);
        this.componentRepository = componentRepository;
    }

    /**
     * Handles the event when a node joins the system.
     *
     * @param bundleRegistration The registration information of the joining bundle.
     */
    private void onNodeJoin(BundleRegistration bundleRegistration) {
        try {
            var lock = lockRegistry.obtain("DISCOVERY:" + bundleRegistration.getBundleId());
            if (!lock.tryLock())
                return;
            try {
                logger.info("Discovering bundle: %s".formatted(bundleRegistration.getBundleId()));
                if (!bundleRegistration.getHandlers().isEmpty()) {
                    var bundle = bundleRepository.findById(bundleRegistration.getBundleId()).orElseGet(() -> {
                                logger.info("Bundle %s not found, creating an ephemeral one".formatted(bundleRegistration.getBundleId()));
                                return bundleRepository.save(new Bundle(
                                        bundleRegistration.getBundleId(),
                                        bundleRegistration.getBundleVersion(),
                                        null,
                                        null,
                                        BucketType.Ephemeral,
                                        bundleRegistration.getInstanceId(),
                                        null,
                                        true,
                                        new HashMap<>(),
                                        new HashMap<>(),
                                        false,
                                        0,
                                        1,
                                        Instant.now()));
                            }
                    );
                    for (RegisteredHandler registeredHandler : bundleRegistration.getHandlers()) {
                        if (!handlerRepository.exists(
                                bundleRegistration.getBundleId(),
                                registeredHandler.getComponentType(),
                                registeredHandler.getComponentName(),
                                registeredHandler.getHandlerType(),
                                registeredHandler.getHandledPayload()
                        )) {
                            logger.info("Handler not found, creating an ephemeral one:");
                            logger.info(registeredHandler.toString());
                            var handler = new Handler();
                            handler.setComponent(componentRepository.findById(registeredHandler.getComponentName()).orElseGet(() -> {
                                var c = new Component();
                                c.setBundle(bundle);
                                c.setComponentName(registeredHandler.getComponentName());
                                c.setComponentType(registeredHandler.getComponentType());
                                c.setUpdatedAt(Instant.now());
                                return componentRepository.save(c);

                            }));
                            handler.setHandledPayload(payloadRepository.findById(registeredHandler.getHandledPayload())
                                    .map(p -> {
                                        if (p.getType() != registeredHandler.getHandledPayloadType()) {
                                            p.setType(registeredHandler.getHandledPayloadType());
                                            return payloadRepository.save(p);
                                        }
                                        return p;
                                    })
                                    .orElseGet(
                                            () -> {
                                                logger.info("Payload %s not found, creating an ephemeral one".formatted(registeredHandler.getHandledPayload()));
                                                var payload = new Payload();
                                                payload.setName(registeredHandler.getHandledPayload());
                                                payload.setJsonSchema("null");
                                                payload.setValidJsonSchema(false);
                                                payload.setType(registeredHandler.getHandledPayloadType());
                                                payload.setUpdatedAt(Instant.now());
                                                payload.setRegisteredIn(bundle.getId());
                                                return payloadRepository.save(payload);
                                            }
                                    ));
                            var type = switch (registeredHandler.getComponentType()) {
                                case Aggregate -> PayloadType.DomainEvent;
                                case Service -> PayloadType.ServiceEvent;
                                case Projection -> PayloadType.View;
                                default -> null;
                            };
                            handler.setReturnType(registeredHandler.getReturnType() == null ? null : payloadRepository.findById(registeredHandler.getReturnType())
                                    .map(p -> {
                                        if (p.getType() != type) {
                                            p.setType(type);
                                            return payloadRepository.save(p);
                                        }
                                        return p;
                                    })
                                    .orElseGet(
                                            () -> {
                                                logger.info("Payload %s not found, creating an ephemeral one".formatted(registeredHandler.getReturnType()));
                                                var payload = new Payload();
                                                payload.setName(registeredHandler.getReturnType());
                                                payload.setJsonSchema("null");
                                                payload.setType(type);
                                                payload.setValidJsonSchema(false);
                                                payload.setUpdatedAt(Instant.now());
                                                payload.setRegisteredIn(bundle.getId());
                                                return payloadRepository.save(payload);
                                            }
                                    ));
                            handler.setHandlerType(registeredHandler.getHandlerType());
                            handler.setReturnIsMultiple(registeredHandler.isReturnIsMultiple());
                            handler.setAssociationProperty(registeredHandler.getAssociationProperty());
                            handler.generateId();
                            handlerRepository.save(handler);
                        }
                    }
                }
                if (bundleRegistration.getPayloadInfo() != null)
                    bundleRegistration.getPayloadInfo().forEach((k, v) -> payloadRepository.findById(k).ifPresent(p -> {
                        p.setJsonSchema(v[0]);
                        p.setDomain(v[1]);
                        p.setValidJsonSchema(true);
                        p.setUpdatedAt(Instant.now());
                        payloadRepository.save(p);
                    }));
            } finally {
                lock.unlock();
            }

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Handles the event when a node leaves the system.
     *
     * @param node The address of the leaving node.
     */
    public void onNodeLeave(NodeAddress node) {
        var lock = lockRegistry.obtain("DISCOVERY:" + node.instanceId());
        lock.lock();
        try {
            bundleRepository.findById(node.bundleId()).ifPresent(b -> {
                if (b.getBucketType().equals(BucketType.Ephemeral) && b.getArtifactCoordinates().equals(node.instanceId())) {
                    bundleService.unregister(node.bundleId());
                }
            });
        } finally {
            lock.unlock();
        }
    }
}
