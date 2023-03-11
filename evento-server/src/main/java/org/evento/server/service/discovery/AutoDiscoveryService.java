package org.evento.server.service.discovery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.modeling.bundle.types.PayloadType;
import org.evento.common.modeling.messaging.message.bus.NodeAddress;
import org.evento.common.modeling.messaging.message.internal.discovery.ClusterNodeApplicationDiscoveryRequest;
import org.evento.common.modeling.messaging.message.internal.discovery.ClusterNodeApplicationDiscoveryResponse;
import org.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import org.evento.server.domain.model.BucketType;
import org.evento.server.domain.model.Bundle;
import org.evento.server.domain.model.Handler;
import org.evento.server.domain.model.Payload;
import org.evento.server.domain.repository.BundleRepository;
import org.evento.server.domain.repository.HandlerRepository;
import org.evento.server.domain.repository.PayloadRepository;
import org.evento.server.service.BundleService;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;

@Service
public class AutoDiscoveryService {
    private final Logger logger = LogManager.getLogger(AutoDiscoveryService.class);
    private final MessageBus messageBus;
    private final BundleRepository bundleRepository;
    private final HandlerRepository handlerRepository;

    private final PayloadRepository payloadRepository;

    private final BundleService bundleService;

    private final LockRegistry lockRegistry;

    public AutoDiscoveryService(MessageBus messageBus, BundleRepository bundleRepository, HandlerRepository handlerRepository, PayloadRepository payloadRepository, BundleService bundleService, LockRegistry lockRegistry) {
        this.messageBus = messageBus;
        this.bundleRepository = bundleRepository;
        this.handlerRepository = handlerRepository;
        this.payloadRepository = payloadRepository;
        this.bundleService = bundleService;
        this.lockRegistry = lockRegistry;
        messageBus.addJoinListener(this::onNodeJoin);
        messageBus.addLeaveListener(this::onNodeLeave);
    }

    private void onNodeJoin(NodeAddress node) {
        if(node.equals(messageBus.getAddress())) return;
        try {
            messageBus.request(node, new ClusterNodeApplicationDiscoveryRequest(), response -> {
                var lock = lockRegistry.obtain("DISCOVERY:" + node.getNodeId());
                lock.lock();
                try {
                    var resp = ((ClusterNodeApplicationDiscoveryResponse) response);
                    logger.info("Discovering bundle: %s".formatted(resp.getBundleId()));
                    if (resp.getHandlers().size() > 0) {
                        var bundle = bundleRepository.findById(resp.getBundleId()).orElseGet(() -> {
                                    logger.info("Bundle %s not found, creating an ephemeral one".formatted(resp.getBundleId()));
                                    return bundleRepository.save(new Bundle(
                                            resp.getBundleId(),
                                            resp.getBundleVersion(),
                                            BucketType.Ephemeral,
                                            node.getNodeId(),
                                            null,
                                            true,
                                            new HashMap<>(),
                                            new HashMap<>(),
                                            resp.getAutorun(),
                                            resp.getMinInstances(),
                                            resp.getMaxInstances()));
                                }
                        );
                        for (RegisteredHandler registeredHandler : resp.getHandlers()) {
                            if (!handlerRepository.exists(
                                    resp.getBundleId(),
                                    registeredHandler.getComponentType(),
                                    registeredHandler.getComponentName(),
                                    registeredHandler.getHandlerType(),
                                    registeredHandler.getHandledPayload()
                            )) {
                                logger.info("Handler not found, creating an ephemeral one:");
                                logger.info(registeredHandler.toString());
                                var handler = new Handler();
                                handler.setBundle(bundle);
                                handler.setHandledPayload(payloadRepository.findById(registeredHandler.getHandledPayload())
                                        .map(p -> {
                                            if(p.getType() != registeredHandler.getHandledPayloadType()){
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
                                            payload.setType(registeredHandler.getHandledPayloadType());
                                            payload.setUpdatedAt(Instant.now());
                                            payload.setRegisteredIn(bundle.getId());
                                            return payloadRepository.save(payload);
                                        }
                                ));
                                handler.setComponentName(registeredHandler.getComponentName());
                                var type = switch (registeredHandler.getComponentType()) {
                                    case Aggregate -> PayloadType.DomainEvent;
                                    case Service -> PayloadType.ServiceEvent;
                                    case Projection -> PayloadType.View;
                                    default -> null;
                                };
                                handler.setReturnType(registeredHandler.getReturnType() == null ? null : payloadRepository.findById(registeredHandler.getReturnType())
                                        .map(p -> {
                                            if(p.getType() != type){
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
                                            payload.setUpdatedAt(Instant.now());
                                            payload.setRegisteredIn(bundle.getId());
                                            return payloadRepository.save(payload);
                                        }
                                ));
                                handler.setComponentType(registeredHandler.getComponentType());
                                handler.setHandlerType(registeredHandler.getHandlerType());
                                handler.setReturnIsMultiple(registeredHandler.isReturnIsMultiple());
                                handler.setAssociationProperty(registeredHandler.getAssociationProperty());
                                handler.generateId();
                                handlerRepository.save(handler);
                            }
                        }
                    }
                }finally {
                    lock.unlock();
                }
            }, logger::error);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    public void onNodeLeave(NodeAddress node) {
        if(node.equals(messageBus.getAddress())) return;
        var lock = lockRegistry.obtain("DISCOVERY:" + node.getNodeId());
        lock.lock();
        try {
            bundleRepository.findById(node.getBundleId()).ifPresent(b -> {
                if(b.getBucketType().equals(BucketType.Ephemeral) && b.getArtifactCoordinates().equals(node.getNodeId())){
                   bundleService.unregister(node.getBundleId());
                }
            });
        }finally {
            lock.unlock();
        }
    }
}