package com.evento.server.service.discovery;

import com.evento.common.utils.PgDistributedLock;
import com.evento.server.domain.model.core.*;
import com.evento.server.domain.repository.core.*;
import com.evento.server.service.HandlerService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.server.bus.BusFacade;
import com.evento.server.bus.NodeAddress;
import com.evento.server.bus.event.BusEvent;
import com.evento.server.service.BundleService;
import com.evento.transport.protocol.BundleDiscoveryInfo;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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

    public AutoDiscoveryService(BusFacade busFacade,
                                BundleRepository bundleRepository,
                                HandlerService handlerService,
                                PayloadRepository payloadRepository, BundleService bundleService,
                                ComponentRepository componentRepository, DataSource dataSource) {
        this.bundleRepository = bundleRepository;
        this.handlerService = handlerService;
        this.payloadRepository = payloadRepository;
        this.bundleService = bundleService;
        busFacade.subscribe(event -> {
            switch (event) {
                case BusEvent.BundleDiscovered disc -> onNodeJoin(disc.node(), disc.discovery());
                case BusEvent.NodeLeft left -> onNodeLeave(left.node());
                default -> { /* ignore other event types */ }
            }
        });
        this.componentRepository = componentRepository;
        this.pgDistributedLock = new PgDistributedLock(dataSource);
    }

    private void onNodeJoin(NodeAddress node, BundleDiscoveryInfo registration) {
        try {
            var key = "DISCOVERY:" + node.bundleId();
            pgDistributedLock.lockedArea(key, () -> {
                logger.info("Discovering bundle: %s".formatted(node.bundleId()));
                if (!registration.handlers().isEmpty()) {
                    var bundle = bundleRepository.findById(node.bundleId()).orElseGet(() -> {
                                logger.info("Bundle %s not found, creating an ephemeral one".formatted(node.bundleId()));
                                return bundleRepository.save(new Bundle(
                                        node.bundleId(),
                                        registration.bundleVersion(),
                                        null,
                                        null,
                                        "L",
                                        null,
                                        BucketType.Ephemeral,
                                        node.instanceId(),
                                        null,
                                        true,
                                        new HashMap<>(),
                                        new HashMap<>(),
                                        false,
                                        false,
                                        Instant.now()));
                            }
                    );

                    // Apply bundle-level metadata from discovery
                    boolean bundleChanged = false;
                    if (!registration.description().isEmpty() &&
                            (bundle.getDescription() == null || bundle.getDescription().isEmpty())) {
                        bundle.setDescription(registration.description());
                        bundleChanged = true;
                    }
                    if (!registration.detail().isEmpty() &&
                            (bundle.getDetail() == null || bundle.getDetail().isEmpty())) {
                        bundle.setDetail(registration.detail());
                        bundleChanged = true;
                    }
                    if (!registration.repositoryUrl().isEmpty() &&
                            (bundle.getRepositoryUrl() == null || bundle.getRepositoryUrl().isEmpty())) {
                        bundle.setRepositoryUrl(registration.repositoryUrl());
                        bundleChanged = true;
                    }
                    if (!registration.linePrefix().isEmpty() &&
                            (bundle.getLinePrefix() == null || bundle.getLinePrefix().isEmpty())) {
                        bundle.setLinePrefix(registration.linePrefix());
                        bundleChanged = true;
                    }
                    if (bundleChanged) bundleRepository.save(bundle);

                    var handlers = handlerService.findAllByBundleId(bundle.getId());
                    for (RegisteredHandler registeredHandler : registration.handlers()) {
                        if (!handlerService.exists(
                                node.bundleId(),
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
                                c.setDescription(registeredHandler.getComponentDescription());
                                c.setDetail(registeredHandler.getComponentDetail());
                                c.setPath(registeredHandler.getComponentPath());
                                c.setLine(registeredHandler.getComponentLine() > 0
                                        ? registeredHandler.getComponentLine() : null);
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
                            handler.setReturnType(registeredHandler.getReturnType() == null ? null :
                                    payloadRepository.findById(registeredHandler.getReturnType())
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
                            handler.setLine(registeredHandler.getHandlerLine() > 0
                                    ? registeredHandler.getHandlerLine() : null);
                            handler.setInvocations(buildInvocations(registeredHandler, bundle.getId()));
                            handler.generateId();
                            handlerService.save(handler);
                        } else {
                            handlers.stream()
                                    .filter(h ->
                                            Objects.equals(h.getComponent().getBundle().getId(), node.bundleId())
                                            && h.getComponent().getComponentName().equals(registeredHandler.getComponentName())
                                            && h.getComponent().getComponentType().equals(registeredHandler.getComponentType())
                                            && h.getHandledPayload().getName().equals(registeredHandler.getHandledPayload())
                                            && h.getHandlerType().equals(registeredHandler.getHandlerType())
                                    )
                                    .findFirst()
                                    .ifPresent(existing -> {
                                        handlers.remove(existing);
                                        boolean changed = false;
                                        if (existing.getInvocations() == null || existing.getInvocations().isEmpty()) {
                                            existing.setInvocations(buildInvocations(registeredHandler, bundle.getId()));
                                            changed = true;
                                        }
                                        if (existing.getLine() == null && registeredHandler.getHandlerLine() > 0) {
                                            existing.setLine(registeredHandler.getHandlerLine());
                                            changed = true;
                                        }
                                        if (changed) handlerService.save(existing);
                                        // Backfill component path/line if missing
                                        var comp = existing.getComponent();
                                        boolean compChanged = false;
                                        if ((comp.getPath() == null || comp.getPath().isEmpty())
                                                && !registeredHandler.getComponentPath().isEmpty()) {
                                            comp.setPath(registeredHandler.getComponentPath());
                                            compChanged = true;
                                        }
                                        if (comp.getLine() == null && registeredHandler.getComponentLine() > 0) {
                                            comp.setLine(registeredHandler.getComponentLine());
                                            compChanged = true;
                                        }
                                        if (compChanged) componentRepository.save(comp);
                                    });
                        }
                    }
                    handlerService.deleteAll(handlers);
                }
                if (registration.payloadInfo() != null)
                    registration.payloadInfo().forEach((k, v) -> payloadRepository.findById(k).ifPresent(p -> {
                        if (v.schema() != null) p.setJsonSchema(v.schema());
                        if (v.domain() != null) p.setDomain(v.domain());
                        if (v.description() != null && !v.description().isEmpty()) p.setDescription(v.description());
                        if (v.detail() != null && !v.detail().isEmpty()) p.setDetail(v.detail());
                        if (v.path() != null && !v.path().isEmpty()) p.setPath(v.path());
                        if (v.line() > 0) p.setLine(v.line());
                        p.setValidJsonSchema(true);
                        p.setUpdatedAt(Instant.now());
                        payloadRepository.save(p);
                    }));
                registration.handlers().stream().map(RegisteredHandler::getComponentName)
                        .distinct().forEach(handlerService::clearCache);
            });

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private Map<Integer, Payload> buildInvocations(RegisteredHandler rh, String bundleId) {
        var invocations = new HashMap<Integer, Payload>();
        rh.getInvokedCommands().forEach((line, name) ->
                invocations.put(line, resolveOrCreatePayload(name, PayloadType.Command, bundleId)));
        rh.getInvokedQueries().forEach((line, name) ->
                invocations.put(line, resolveOrCreatePayload(name, PayloadType.Query, bundleId)));
        return invocations;
    }

    private Payload resolveOrCreatePayload(String name, PayloadType type, String bundleId) {
        return payloadRepository.findById(name).orElseGet(() -> {
            var payload = new Payload();
            payload.setName(name);
            payload.setJsonSchema("null");
            payload.setType(type);
            payload.setUpdatedAt(Instant.now());
            payload.setRegisteredIn(bundleId);
            payload.setValidJsonSchema(false);
            return payloadRepository.save(payload);
        });
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
