package com.evento.server.service;

import com.evento.server.domain.model.core.Bundle;
import com.evento.server.domain.model.core.Handler;
import com.evento.server.domain.model.core.Payload;
import com.evento.server.domain.repository.core.*;
import com.evento.server.domain.repository.core.projection.BundleListProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The BundleService class provides methods for managing bundles in the system.
 */
@Service
public class BundleService {

    private static final Logger log = LoggerFactory.getLogger(BundleService.class);

    private final BundleRepository bundleRepository;

    private final HandlerService handlerService;
    private final PayloadRepository payloadRepository;

    private final ComponentRepository componentRepository;

    private final ConsumerRepository consumerRepository;

    /**
     * The BundleService class is responsible for managing bundles in the system.
     */
    public BundleService(BundleRepository bundleRepository, HandlerService handlerService, PayloadRepository payloadRepository,
                         ComponentRepository componentRepository, ConsumerRepository consumerRepository) {
        this.bundleRepository = bundleRepository;
        this.handlerService = handlerService;
        this.payloadRepository = payloadRepository;
        this.componentRepository = componentRepository;
        this.consumerRepository = consumerRepository;
    }

    /**
     * Unregisters a bundle and deletes all associated handlers, components, and payloads.
     *
     * @param bundleId the ID of the bundle to unregister
     */
    public void unregister(
            String bundleId) {
        log.info("[BundleService] Unregister called for bundleId={}", bundleId);
        int removedHandlers = 0;
        for (Handler handler : handlerService.findAll()) {
            if (!handler.getComponent().getBundle().getId().equals(bundleId)) continue;
            log.info("[BundleService] Deleting handler id={} type={} component={} bundle={}", handler.getUuid(), handler.getHandlerType(), handler.getComponent().getComponentName(), bundleId);
            handlerService.delete(handler);
            removedHandlers++;
            handler.getHandledPayload().getHandlers().remove(handler);
        }
        log.info("[BundleService] Removed {} handlers for bundleId={}", removedHandlers, bundleId);
        var components = componentRepository.findAllByBundle_Id(bundleId);
        consumerRepository.deleteAllByComponentIn(components);
        log.info("[BundleService] Removed {} consumers for bundleId={}", components.size(), bundleId);

        for (var c : components) {
            log.info("[BundleService] Deleting component name='{}' type='{}' from bundleId={}", c.getComponentName(), c.getComponentType(), bundleId);
        }
        componentRepository.deleteAll(components);
        log.info("[BundleService] Removed {} components for bundleId={}", components.size(), bundleId);

        var deletedBundle = bundleRepository.findById(bundleId).orElse(null);
        if (deletedBundle != null) {
            bundleRepository.delete(deletedBundle);
            log.info("[BundleService] Deleted bundle record for bundleId={}", bundleId);
        } else {
            log.info("[BundleService] Bundle record not found for bundleId={}", bundleId);
        }
        int removedOrphanPayloads = 0;
        for (Payload payload : payloadRepository.findAll()) {
            if (!bundleRepository.existsById(payload.getRegisteredIn())
                    && !handlerService.isPayloadReferenced(payload.getName())) {
                log.info("[BundleService] Deleting orphan payload name='{}' previously registeredIn={}", payload.getName(), payload.getRegisteredIn());
                payloadRepository.delete(payload);
                removedOrphanPayloads++;
            }
        }
        components.forEach(c -> handlerService.clearCache(c.getComponentName()));
        log.info("[BundleService] Removed {} orphan payloads after unregister of bundleId={}", removedOrphanPayloads, bundleId);
    }

    /**
     * Retrieves a list of all bundles in the system.
     *
     * @return A list of bundles.
     */
    public List<Bundle> findAllBundles() {
        log.info("[BundleService] Retrieving all bundles");
        return bundleRepository.findAll();
    }

    /**
     * Retrieves a list of BundleListProjection objects representing a projected view of Bundle objects.
     * <p>
     * The findAllProjection method executes a native SQL query to fetch the required data from the database.
     *
     * @return A list of BundleListProjection objects representing the projected view of Bundle objects.
     * @see BundleListProjection
     */
    public List<BundleListProjection> findAllProjection() {
        log.info("[BundleService] Retrieving bundle projections");
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
        log.info("[BundleService] Retrieving bundle by id={}", bundleId);
        return bundleRepository.findById(bundleId).orElseThrow();
    }
}
