package org.eventrails.server.service.discovery;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.modeling.bundle.types.PayloadType;
import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.common.modeling.messaging.message.internal.discovery.ClusterNodeApplicationDiscoveryRequest;
import org.eventrails.common.modeling.messaging.message.internal.discovery.ClusterNodeApplicationDiscoveryResponse;
import org.eventrails.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import org.eventrails.server.domain.model.BucketType;
import org.eventrails.server.domain.model.Bundle;
import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.model.Payload;
import org.eventrails.server.domain.repository.BundleRepository;
import org.eventrails.server.domain.repository.HandlerRepository;
import org.eventrails.server.domain.repository.PayloadRepository;
import org.eventrails.server.service.BundleService;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.Instant;

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
        try {
            messageBus.request(node, new ClusterNodeApplicationDiscoveryRequest(), response -> {
                var lock = lockRegistry.obtain("DISCOVERY:" + node.getNodeId());
                lock.lock();
                try {
                    var resp = ((ClusterNodeApplicationDiscoveryResponse) response);
                    logger.info("Discovering bundle: %s".formatted(resp.getBundleName()));
                    if (resp.getHandlers().size() > 0) {
                        var bundle = bundleRepository.findById(resp.getBundleName()).orElseGet(() -> {
                                    logger.info("Bundle %s not found, creating an ephemeral one".formatted(resp.getBundleName()));
                                    return bundleRepository.save(new Bundle(resp.getBundleName(), BucketType.Ephemeral, node.getNodeId(), null, true));
                                }
                        );
                        for (RegisteredHandler registeredHandler : resp.getHandlers()) {
                            if (!handlerRepository.exists(
                                    resp.getBundleName(),
                                    registeredHandler.getComponentType(),
                                    registeredHandler.getComponentName(),
                                    registeredHandler.getHandlerType(),
                                    registeredHandler.getHandledPayload()
                            )) {
                                logger.info("Handler not found, creating an ephemeral one:");
                                logger.info(registeredHandler.toString());
                                var handler = new Handler();
                                handler.setBundle(bundle);
                                handler.setHandledPayload(payloadRepository.findById(registeredHandler.getHandledPayload()).orElseGet(
                                        () -> {
                                            logger.info("Payload %s not found, creating an ephemeral one".formatted(registeredHandler.getHandledPayload()));
                                            var payload = new Payload();
                                            payload.setName(registeredHandler.getHandledPayload());
                                            payload.setJsonSchema("null");
                                            payload.setType(registeredHandler.getHandledPayloadType());
                                            payload.setUpdatedAt(Instant.now());
                                            payload.setRegisteredIn(bundle.getName());
                                            return payloadRepository.save(payload);
                                        }
                                ));
                                handler.setComponentName(registeredHandler.getComponentName());
                                handler.setReturnType(registeredHandler.getReturnType() == null ? null : payloadRepository.findById(registeredHandler.getReturnType()).orElseGet(
                                        () -> {
                                            logger.info("Payload %s not found, creating an ephemeral one".formatted(registeredHandler.getReturnType()));
                                            var payload = new Payload();
                                            payload.setName(registeredHandler.getReturnType());
                                            payload.setJsonSchema("null");
                                            payload.setType(switch (registeredHandler.getComponentType()) {
                                                case Aggregate -> PayloadType.DomainEvent;
                                                case Service -> PayloadType.ServiceEvent;
                                                case Projection -> PayloadType.View;
                                                default -> null;
                                            });
                                            payload.setUpdatedAt(Instant.now());
                                            payload.setRegisteredIn(bundle.getName());
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
        var lock = lockRegistry.obtain("DISCOVERY:" + node.getNodeId());
        lock.lock();
        try {
            bundleRepository.findById(node.getNodeName()).ifPresent(b -> {
                if(b.getBucketType().equals(BucketType.Ephemeral) && b.getArtifactCoordinates().equals(node.getNodeId())){
                   bundleService.unregister(node.getNodeName());
                }
            });
        }finally {
            lock.unlock();
        }
    }
}
