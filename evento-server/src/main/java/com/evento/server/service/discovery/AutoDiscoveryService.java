package com.evento.server.service.discovery;

import com.evento.common.utils.PgDistributedLock;
import com.evento.server.domain.model.core.*;
import com.evento.server.domain.repository.core.*;
import com.evento.server.service.HandlerService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleRegistration;
import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.server.bus.MessageBus;
import com.evento.server.bus.NodeAddress;
import com.evento.server.service.BundleService;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;

/**
 * Service class for auto-discovery of bundles, handlers, and payloads.
 */
@Service
public class AutoDiscoveryService {
    private static final Logger logger = LogManager.getLogger(AutoDiscoveryService.class);
    private final BundleRepository bundleRepository;
    private final HandlerService handlerService;
    private final PayloadRepository payloadRepository;
    private final BundleService bundleService;
    private final ComponentRepository componentRepository;

    private final PgDistributedLock pgDistributedLock;

    /**
     * Constructs an instance of the AutoDiscoveryService responsible for managing the
     * discovery of bundles, components, handlers, and payloads in a distributed environment.
     *
     * @param messageBus The message bus used for inter-service communication and event handling.
     * @param bundleRepository Repository for managing bundle entities.
     * @param handlerService Service for managing handler entities.
     * @param payloadRepository Repository for managing payload entities.
     * @param bundleService Service responsible for bundle-related operations.
     * @param componentRepository Repository for managing component entities.
     * @param dataSource The data source used for distributed locking and database interactions.
     */
    public AutoDiscoveryService(MessageBus messageBus,
                                BundleRepository bundleRepository,
                                HandlerService handlerService,
                                PayloadRepository payloadRepository, BundleService bundleService,
                                ComponentRepository componentRepository, DataSource dataSource) {
        this.bundleRepository = bundleRepository;
        this.handlerService = handlerService;
        this.payloadRepository = payloadRepository;
        this.bundleService = bundleService;
        messageBus.addJoinListener(this::onNodeJoin);
        messageBus.addLeaveListener(this::onNodeLeave);
        this.componentRepository = componentRepository;
        this.pgDistributedLock = new PgDistributedLock(dataSource);
    }

    /**
     * Handles the event when a node joins the system.
     *
     * @param bundleRegistration The registration information of the joining bundle.
     */
    private void onNodeJoin(BundleRegistration bundleRegistration) {
        try {
            var key = "DISCOVERY:" + bundleRegistration.getBundleId();
            pgDistributedLock.lockedArea(key, () -> {
                logger.info("Discovering bundle: %s".formatted(bundleRegistration.getBundleId()));
                if (!bundleRegistration.getHandlers().isEmpty()) {
                    var bundle = bundleRepository.findById(bundleRegistration.getBundleId()).orElseGet(() -> {
                                logger.info("Bundle %s not found, creating an ephemeral one".formatted(bundleRegistration.getBundleId()));
                                return bundleRepository.save(new Bundle(
                                        bundleRegistration.getBundleId(),
                                        bundleRegistration.getBundleVersion(),
                                        null,
                                        null,
                                        "L",
                                        BucketType.Ephemeral,
                                        bundleRegistration.getInstanceId(),
                                        null,
                                        true,
                                        new HashMap<>(),
                                        new HashMap<>(),
                                        false,
                                        false,
                                        0,
                                        1,
                                        Instant.now()));
                            }
                    );
                    var handlers = handlerService.findAllByBundleId(bundle.getId());
                    for (RegisteredHandler registeredHandler : bundleRegistration.getHandlers()) {
                        if (!handlerService.exists(
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
                            handlerService.save(handler);
                        } else {
                            handlers.removeIf(h ->
                                    Objects.equals(h.getComponent().getBundle().getId(), bundleRegistration.getBundleId())
                                            && h.getComponent().getComponentName().equals(registeredHandler.getComponentName())
                                            && h.getComponent().getComponentType().equals(registeredHandler.getComponentType())
                                            && h.getHandledPayload().getName().equals(registeredHandler.getHandledPayload())
                                            && h.getHandlerType().equals(registeredHandler.getHandlerType())
                            );
                        }
                    }
                    handlerService.deleteAll(handlers);
                }
                if (bundleRegistration.getPayloadInfo() != null)
                    bundleRegistration.getPayloadInfo().forEach((k, v) -> payloadRepository.findById(k).ifPresent(p -> {
                        p.setJsonSchema(v[0]);
                        p.setDomain(v[1]);
                        p.setValidJsonSchema(true);
                        p.setUpdatedAt(Instant.now());
                        payloadRepository.save(p);
                    }));
                bundleRegistration.getHandlers().stream().map(RegisteredHandler::getComponentName)
                        .distinct().forEach(handlerService::clearCache);
            });

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
        try {
            var key = "DISCOVERY:" + node.instanceId();
            pgDistributedLock.lockedArea(key, () -> {
                bundleRepository.findById(node.bundleId()).ifPresent(b -> {
                    if (b.getBucketType().equals(BucketType.Ephemeral) && b.getArtifactCoordinates().equals(node.instanceId())) {
                        bundleService.unregister(node.bundleId());
                    }
                });
            });
        }catch (Exception e){
            logger.error(e.getMessage(), e);
        }
    }
}
