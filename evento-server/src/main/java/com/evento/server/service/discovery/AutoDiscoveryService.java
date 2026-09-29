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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final ConsumerService consumerService;
    private final BusFacade busFacade;
    private final BundlePresence presence;
    private final CatalogWriter catalogWriter;
    /** Null in unit tests: catalog passes then run without a surrounding transaction. */
    private final TransactionTemplate tx;

    private final PgDistributedLock pgDistributedLock;

    public AutoDiscoveryService(BusFacade busFacade,
                                BundleRepository bundleRepository,
                                HandlerService handlerService,
                                PayloadRepository payloadRepository, BundleService bundleService,
                                ComponentRepository componentRepository,
                                ConsumerService consumerService, DataSource dataSource,
                                BundlePresence presence,
                                CatalogWriter catalogWriter,
                                PlatformTransactionManager transactionManager) {
        this.bundleRepository = bundleRepository;
        this.handlerService = handlerService;
        this.payloadRepository = payloadRepository;
        this.bundleService = bundleService;
        this.consumerService = consumerService;
        this.busFacade = busFacade;
        this.presence = presence;
        this.catalogWriter = catalogWriter;
        this.tx = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        this.componentRepository = componentRepository;
        this.pgDistributedLock = new PgDistributedLock(dataSource);
        busFacade.subscribe(event -> {
            switch (event) {
                case BusEvent.NodeJoined joined -> onSessionOpened(joined.node());
                case BusEvent.BundleDiscovered disc -> onNodeJoin(disc.node(), disc.discovery());
                case BusEvent.NodeLeft left -> onNodeLeave(left.node());
                default -> { /* ignore other event types */ }
            }
        });
    }

    /**
     * Claims the instance's session for this server before anything else about the
     * instance is written: the claim is what tells a server that still holds a stale
     * session of the same instance to keep its hands off on the eventual NodeLeft.
     * Joins and leaves of one server are published in order on its bus (a superseded
     * session's NodeLeft precedes the replacement's NodeJoined), so the claim never
     * races a leave on the same server.
     */
    private void onSessionOpened(NodeAddress node) {
        try {
            presence.claim(node);
        } catch (Exception e) {
            logger.error("Failed to record the session of instance %s (bundle %s) on this server"
                    .formatted(node.instanceId(), node.bundleId()), e);
        }
    }

    private void onNodeJoin(NodeAddress node, BundleDiscoveryInfo registration) {
        try {
            var key = ConsumerService.catalogLockKey(node.bundleId());
            pgDistributedLock.lockedArea(key, () -> {
                // One transaction per discovery pass: a failure part-way used to leave
                // the catalog half-written (some handlers without their payloads).
                ConsumerService.inTransaction(tx, () -> discover(node, registration));
                registration.handlers().stream().map(RegisteredHandler::getComponentName)
                        .distinct().forEach(handlerService::clearCache);
            });

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void discover(NodeAddress node, BundleDiscoveryInfo registration) {
                logger.info("Discovering bundle: %s".formatted(node.bundleId()));
                if (!registration.handlers().isEmpty()) {
                    var bundle = bundleRepository.findById(node.bundleId()).orElseGet(() -> {
                                if (catalogWriter.insertBundleIfAbsent(
                                        node.bundleId(),
                                        registration.bundleVersion(),
                                        registration.description(),
                                        registration.detail(),
                                        registration.linePrefix(),
                                        registration.repositoryUrl(),
                                        node.instanceId(),
                                        true)) {
                                    logger.info("Bundle %s not found, created one from discovery".formatted(node.bundleId()));
                                }
                                return bundleRepository.findById(node.bundleId()).orElseThrow(() ->
                                        new IllegalStateException("Bundle %s vanished during discovery"
                                                .formatted(node.bundleId())));
                            }
                    );

                    // Apply bundle-level metadata from discovery. The bundle is the source of truth
                    // for its own metadata, so a non-empty reported value that differs from what is
                    // stored overwrites it (empty values never clobber an existing value).
                    boolean bundleChanged = false;
                    if (!registration.description().isEmpty() &&
                            !registration.description().equals(bundle.getDescription())) {
                        bundle.setDescription(registration.description());
                        bundleChanged = true;
                    }
                    if (!registration.detail().isEmpty() &&
                            !registration.detail().equals(bundle.getDetail())) {
                        bundle.setDetail(registration.detail());
                        bundleChanged = true;
                    }
                    if (!registration.repositoryUrl().isEmpty() &&
                            !registration.repositoryUrl().equals(bundle.getRepositoryUrl())) {
                        bundle.setRepositoryUrl(registration.repositoryUrl());
                        bundleChanged = true;
                    }
                    if (!registration.linePrefix().isEmpty() &&
                            !registration.linePrefix().equals(bundle.getLinePrefix())) {
                        bundle.setLinePrefix(registration.linePrefix());
                        bundleChanged = true;
                    }
                    // An ephemeral bundle row records the instance that created it so
                    // onNodeLeave can reclaim it. Transfer that ownership to every
                    // joining instance: during a rolling restart the replacement joins
                    // before the old socket dies, and without the transfer the old
                    // instance's NodeLeft tore down the live instance's registration
                    // (components deleted → consumer registration silently dropped).
                    // A null instanceId marks a manually published bundle and is never
                    // claimed, so those keep surviving node departures.
                    if (bundle.getInstanceId() != null && !bundle.getInstanceId().equals(node.instanceId())) {
                        bundle.setInstanceId(node.instanceId());
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
                                catalogWriter.insertComponentIfAbsent(
                                        registeredHandler.getComponentName(),
                                        registeredHandler.getComponentType(),
                                        bundle.getId(),
                                        registeredHandler.getComponentDescription(),
                                        registeredHandler.getComponentDetail(),
                                        registeredHandler.getComponentPath(),
                                        registeredHandler.getComponentLine() > 0
                                                ? registeredHandler.getComponentLine() : null);
                                return componentRepository.findById(registeredHandler.getComponentName())
                                        .orElseThrow(() -> new IllegalStateException(
                                                "Component %s vanished during discovery"
                                                        .formatted(registeredHandler.getComponentName())));
                            }));
                            handler.setHandledPayload(ensurePayload(registeredHandler.getHandledPayload(),
                                    registeredHandler.getHandledPayloadType(), bundle.getId(), true));
                            var type = switch (registeredHandler.getComponentType()) {
                                case Aggregate -> PayloadType.DomainEvent;
                                case Service -> PayloadType.ServiceEvent;
                                case Projection -> PayloadType.View;
                                default -> null;
                            };
                            handler.setReturnType(registeredHandler.getReturnType() == null ? null :
                                    ensurePayload(registeredHandler.getReturnType(), type, bundle.getId(), true));
                            handler.setHandlerType(registeredHandler.getHandlerType());
                            handler.setReturnIsMultiple(registeredHandler.isReturnIsMultiple());
                            handler.setAssociationProperty(registeredHandler.getAssociationProperty());
                            handler.setLine(registeredHandler.getHandlerLine() > 0
                                    ? registeredHandler.getHandlerLine() : null);
                            handler.setExecutor(registeredHandler.getExecutor());
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
                                        // Not a backfill: a redeploy can move a handler
                                        // between inline and async, so the stored value must
                                        // track the live one rather than stick at whatever
                                        // was first seen.
                                        var executor = registeredHandler.getExecutor();
                                        if (!Objects.equals(
                                                existing.getExecutor() == null ? "" : existing.getExecutor(),
                                                executor)) {
                                            existing.setExecutor(executor);
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
    }

    private Map<Integer, Payload> buildInvocations(RegisteredHandler rh, String bundleId) {
        var invocations = new HashMap<Integer, Payload>();
        rh.getInvokedCommands().forEach((line, name) ->
                invocations.put(line, ensurePayload(name, PayloadType.Command, bundleId, false)));
        rh.getInvokedQueries().forEach((line, name) ->
                invocations.put(line, ensurePayload(name, PayloadType.Query, bundleId, false)));
        return invocations;
    }

    /**
     * Returns the payload row, creating a placeholder when it is missing. Payload names
     * are shared across bundles, so two bundles discovered at once (under different
     * per-bundle lock keys, possibly on different servers) can both miss the row; the
     * ON CONFLICT insert lets the second one read what the first wrote instead of
     * failing on {@code core__payload_pkey}.
     *
     * @param alignType when true, an existing row whose type differs is corrected to {@code type}
     */
    private Payload ensurePayload(String name, PayloadType type, String bundleId, boolean alignType) {
        var p = payloadRepository.findById(name).orElseGet(() -> {
            if (catalogWriter.insertPayloadIfAbsent(name, type, bundleId)) {
                logger.info("Payload %s not found, created an ephemeral one".formatted(name));
            }
            return payloadRepository.findById(name).orElseThrow(() ->
                    new IllegalStateException("Payload %s vanished during discovery".formatted(name)));
        });
        if (alignType && p.getType() != type) {
            p.setType(type);
            return payloadRepository.save(p);
        }
        return p;
    }

    /**
     * Handles the event when a node leaves the system.
     *
     * @param node The address of the leaving node.
     */
    public void onNodeLeave(NodeAddress node) {
        try {
            // Same lock key family as onNodeJoin ("DISCOVERY:" + bundleId): join and
            // leave both touch bundle-scoped rows (components, handlers, the bundle
            // itself), so they must serialize per bundle. The old instance-scoped key
            // let a leave interleave with a join for the same bundle.
            var key = ConsumerService.catalogLockKey(node.bundleId());
            var removed = new AtomicBoolean();
            pgDistributedLock.lockedArea(key, () -> ConsumerService.inTransaction(tx, () -> {
                // The instance may have failed over to another server before this one
                // noticed the dead socket. That server has since re-registered it, so
                // this departure is stale: clearing the consumer rows or unregistering
                // the bundle here would wipe the live registration cluster-wide. The
                // conditional delete holds the presence row lock until this transaction
                // commits, so a concurrent claim by another server waits for the cleanup
                // below instead of interleaving with it.
                if (presence.release(node) == BundlePresence.Release.OWNED_ELSEWHERE) {
                    logger.info("Skipping leave cleanup of instance %s (bundle %s): its session is now held by server %s"
                            .formatted(node.instanceId(), node.bundleId(),
                                    presence.ownerOf(node.instanceId()).orElse("?")));
                    return;
                }
                // Consumer rows are per-instance; the instance is gone, so its
                // rows go with it. Bundles (>= this version) re-send their
                // consumer registration on every re-established session, so a
                // reconnect repopulates them. This replaces the old cleanup
                // that ConsumerController did destructively inside a GET.
                consumerService.clearInstance(node.instanceId());
                bundleRepository.findById(node.bundleId()).ifPresent(b -> {
                    // A null instanceId marks a manually published bundle: never reclaimed.
                    if (b.getInstanceId() == null) return;
                    // Reclaim the ephemeral registration only when no instance of the
                    // bundle is connected anymore, here or on another server sharing this
                    // catalog: a live instance still needs the components and handlers,
                    // and wiping them here made its later consumer registration fail on
                    // missing component rows. The last instance out reclaims, whichever
                    // it is: requiring the leaver to be the instance recorded on the row
                    // leaked the bundle whenever the recorded one left first while another
                    // instance (on this server or another) was still connected.
                    boolean bundleStillLive = busFacade.currentView().stream()
                            .anyMatch(n -> node.bundleId().equals(n.bundleId()))
                            || presence.liveElsewhere(node.bundleId(), node.instanceId());
                    if (bundleStillLive) {
                        logger.info("Skipping unregister of bundle %s: another live instance is connected"
                                .formatted(node.bundleId()));
                        return;
                    }
                    bundleService.removeBundle(node.bundleId());
                    removed.set(true);
                });
            }));
            // Payload rows are shared across bundles, so their sweep runs after the commit,
            // one best-effort delete at a time (see BundleService#sweepOrphanPayloads).
            if (removed.get()) bundleService.sweepOrphanPayloads();
        }catch (Exception e){
            logger.error(e.getMessage(), e);
        }
    }
}
