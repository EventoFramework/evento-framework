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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service class for auto-discovery of bundles, handlers, and payloads.
 */
@Service
public class AutoDiscoveryService {
    private static final Logger logger = LogManager.getLogger(AutoDiscoveryService.class);
    /** Total attempts of a discovery pass that loses a race with another server's catalog writes. */
    static final int MAX_DISCOVERY_ATTEMPTS = 3;
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
     *
     * <p>A leave of the same instance on this server can still race the claim: a
     * superseded session's NodeLeft precedes the replacement's NodeJoined, but a
     * session closed by its channel publishes NodeLeft on that channel's event loop,
     * possibly after the replacement (on another event loop) has already claimed.
     * {@link #onNodeLeave} therefore re-checks the local view after releasing and
     * re-claims an instance that is still connected here.
     */
    private void onSessionOpened(NodeAddress node) {
        try {
            presence.claim(node);
        } catch (Exception e) {
            logger.error("Failed to record the session of instance %s (bundle %s) on this server"
                    .formatted(node.instanceId(), node.bundleId()), e);
        }
    }

    /**
     * Refreshes this server's presence rows. A server that crashed and never came back
     * under the same id stops refreshing, so other servers stop counting its instances
     * as live once the rows go stale (see {@link BundlePresence#liveElsewhere}).
     */
    @Scheduled(fixedDelayString = "${evento.server.presence.heartbeat-interval-ms:30000}",
            initialDelayString = "${evento.server.presence.heartbeat-interval-ms:30000}")
    public void heartbeatPresence() {
        try {
            presence.heartbeat(busFacade.currentView());
        } catch (Exception e) {
            logger.warn("Failed to refresh this server's bundle presence rows", e);
        }
    }

    private void onNodeJoin(NodeAddress node, BundleDiscoveryInfo registration) {
        var key = ConsumerService.catalogLockKey(node.bundleId());
        for (int attempt = 1; ; attempt++) {
            try {
                pgDistributedLock.lockedArea(key, () -> {
                    // Shared payload rows are created up front, in name order and each in
                    // its own statement, so the discovery transaction below never inserts
                    // or updates a row that another bundle's discovery also writes. Two
                    // bundles declaring overlapping payloads in different orders used to
                    // lock them in different orders and deadlock (40P01).
                    referencedPayloads(registration).forEach((name, type) ->
                            catalogWriter.insertPayloadIfAbsent(name, type, node.bundleId()));
                    // One transaction per discovery pass: a failure part-way used to leave
                    // the catalog half-written (some handlers without their payloads).
                    var typeAlignments = new TreeMap<String, PayloadType>();
                    ConsumerService.inTransaction(tx, () -> discover(node, registration, typeAlignments));
                    // Shared-row corrections run after the commit, name-ordered, one
                    // statement each, and write only when something actually changes.
                    typeAlignments.forEach(catalogWriter::alignPayloadType);
                    if (registration.payloadInfo() != null)
                        new TreeMap<>(registration.payloadInfo()).forEach(catalogWriter::backfillPayloadInfo);
                    registration.handlers().stream().map(RegisteredHandler::getComponentName)
                            .distinct().forEach(handlerService::clearCache);
                });
                return;
            } catch (Exception e) {
                if (attempt < MAX_DISCOVERY_ATTEMPTS && isTransientCatalogConflict(e)) {
                    logger.warn("Discovery of bundle %s (instance %s) hit a concurrent catalog change, retrying (attempt %d): %s"
                            .formatted(node.bundleId(), node.instanceId(), attempt, rootMessage(e)));
                    try {
                        Thread.sleep(ThreadLocalRandom.current().nextLong(50, 250) * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Discovery of bundle %s interrupted while retrying".formatted(node.bundleId()), e);
                        return;
                    }
                    continue;
                }
                logger.error(e.getMessage(), e);
                return;
            }
        }
    }

    /**
     * A discovery pass that lost a race with another server's catalog writes: a
     * deadlock or serialization failure, a payload the orphan sweep deleted between
     * the up-front insert and the handler insert (foreign key violation), or a row
     * that vanished between insert and read. Re-running the whole pass is safe: it is
     * idempotent, and the failed attempt rolled back.
     */
    static boolean isTransientCatalogConflict(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof CatalogRaceException) return true;
            if (t instanceof SQLException sql) {
                var state = sql.getSQLState();
                if ("40P01".equals(state) || "40001".equals(state) || "23503".equals(state)) return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage();
    }

    /** Every payload name a registration references, with the type its placeholder row gets. */
    static SortedMap<String, PayloadType> referencedPayloads(BundleDiscoveryInfo registration) {
        var declared = new TreeMap<String, PayloadType>();
        var invoked = new TreeMap<String, PayloadType>();
        for (RegisteredHandler rh : registration.handlers()) {
            if (rh.getHandledPayload() != null) declared.put(rh.getHandledPayload(), rh.getHandledPayloadType());
            if (rh.getReturnType() != null) declared.putIfAbsent(rh.getReturnType(), returnPayloadType(rh));
            if (rh.getInvokedCommands() != null)
                rh.getInvokedCommands().values().forEach(n -> invoked.putIfAbsent(n, PayloadType.Command));
            if (rh.getInvokedQueries() != null)
                rh.getInvokedQueries().values().forEach(n -> invoked.putIfAbsent(n, PayloadType.Query));
        }
        invoked.forEach(declared::putIfAbsent);
        return declared;
    }

    private static PayloadType returnPayloadType(RegisteredHandler rh) {
        if (rh.getComponentType() == null) return null;
        return switch (rh.getComponentType()) {
            case Aggregate -> PayloadType.DomainEvent;
            case Service -> PayloadType.ServiceEvent;
            case Projection -> PayloadType.View;
            default -> null;
        };
    }

    private void discover(NodeAddress node, BundleDiscoveryInfo registration,
                          Map<String, PayloadType> typeAlignments) {
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
                                        new CatalogRaceException("Bundle %s vanished during discovery"
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
                                        .orElseThrow(() -> new CatalogRaceException(
                                                "Component %s vanished during discovery"
                                                        .formatted(registeredHandler.getComponentName())));
                            }));
                            handler.setHandledPayload(ensurePayload(registeredHandler.getHandledPayload(),
                                    registeredHandler.getHandledPayloadType(), bundle.getId(), typeAlignments));
                            handler.setReturnType(registeredHandler.getReturnType() == null ? null :
                                    ensurePayload(registeredHandler.getReturnType(),
                                            returnPayloadType(registeredHandler), bundle.getId(), typeAlignments));
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
                // Payload metadata (payloadInfo) is backfilled after the commit: see onNodeJoin.
    }

    private Map<Integer, Payload> buildInvocations(RegisteredHandler rh, String bundleId) {
        var invocations = new HashMap<Integer, Payload>();
        rh.getInvokedCommands().forEach((line, name) ->
                invocations.put(line, ensurePayload(name, PayloadType.Command, bundleId, null)));
        rh.getInvokedQueries().forEach((line, name) ->
                invocations.put(line, ensurePayload(name, PayloadType.Query, bundleId, null)));
        return invocations;
    }

    /**
     * Returns the payload row a new handler references, pinned for the rest of the
     * discovery transaction ({@link CatalogWriter#lockPayload}) so the orphan sweep of a
     * concurrent bundle removal cannot delete it before the handler rows are flushed.
     * The row normally exists already (onNodeJoin creates every referenced payload up
     * front); the insert here only covers a sweep that got in between. Payload names
     * are shared across bundles, so the insert is ON CONFLICT DO NOTHING.
     *
     * <p>The row itself is never modified here: an update inside this transaction
     * would hold the shared row's lock until commit, in whatever order the handlers
     * came, and two discoveries would deadlock. A needed type correction is recorded in
     * {@code typeAlignments} (null: none wanted) and applied after the commit.
     */
    private Payload ensurePayload(String name, PayloadType type, String bundleId,
                                  Map<String, PayloadType> typeAlignments) {
        if (!catalogWriter.lockPayload(name)
                && catalogWriter.insertPayloadIfAbsent(name, type, bundleId)) {
            logger.info("Payload %s not found, created an ephemeral one".formatted(name));
        }
        var p = payloadRepository.findById(name).orElseThrow(() ->
                new CatalogRaceException("Payload %s vanished during discovery".formatted(name)));
        if (typeAlignments != null && p.getType() != type) typeAlignments.put(name, type);
        return p;
    }

    /** A catalog row removed by another server mid-pass; the pass is retried. */
    static final class CatalogRaceException extends IllegalStateException {
        CatalogRaceException(String message) {
            super(message);
        }
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
                // A newer session of the same instance may already be registered here: a
                // superseded session's NodeLeft is published after the replacement is in
                // the view, and a channel-close NodeLeft (published on the old channel's
                // event loop) can land after the replacement's NodeJoined claim, in which
                // case the release above just deleted the live session's row. Checking
                // the view after the release covers both orders: a replacement that
                // enters the view later claims again when its NodeJoined is published
                // (waiting on this transaction's row lock). Its consumer rows are left
                // alone too: its own registration, serialized with us by the lock, owns them.
                if (busFacade.currentView().contains(node)) {
                    presence.claim(node);
                    logger.info("Skipping leave cleanup of instance %s (bundle %s): a newer session of it is connected here"
                            .formatted(node.instanceId(), node.bundleId()));
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
