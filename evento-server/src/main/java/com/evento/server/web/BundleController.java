package com.evento.server.web;

import com.evento.server.domain.repository.core.projection.BundleListProjection;
import com.evento.server.service.BundleService;
import com.evento.server.service.HandlerService;
import com.evento.server.web.dto.BundleDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The {@code BundleController} class is a controller class that handles HTTP requests for inspecting bundles
 * known to the server. Bundles register themselves at runtime via the discovery protocol; this controller
 * only exposes read access and catalog removal.
 */
@RestController
@RequestMapping("api/bundle")
public class BundleController {

    private final BundleService bundleService;
    private final HandlerService handlerService;

    /**
     * Creates a new instance of the BundleController class.
     *
     * @param bundleService  the bundle service used to handle bundle operations
     * @param handlerService the handler service used to handle bundle handler operations
     */
    public BundleController(BundleService bundleService, HandlerService handlerService) {
        this.bundleService = bundleService;
        this.handlerService = handlerService;
    }


    /**
     * Retrieves all bundles.
     * <p>
     * This method sends a GET request to the "/" endpoint with "application/json" as the produces value.
     * The user must have the "ROLE_WEB" role to access this endpoint.
     *
     * @return Returns a ResponseEntity with the list of BundleListProjection objects representing the bundles.
     */
    @GetMapping(value = "/", produces = "application/json")
    @Secured("ROLE_WEB")
    public ResponseEntity<List<BundleListProjection>> findAll() {
        return ResponseEntity.ok(bundleService.findAllProjection());
    }

    /**
     * Retrieves a BundleDto by its name.
     * <p>
     * This method sends a GET request to the "/{name}" endpoint with "application/json" as the produces value.
     * The user must have the "ROLE_WEB" role to access this endpoint.
     *
     * @param name the name of the bundle to retrieve
     * @return a ResponseEntity with the BundleDto object representing the bundle
     */
    @GetMapping(value = "/{name}", produces = "application/json")
    @Secured("ROLE_WEB")
    public ResponseEntity<BundleDto> findById(@PathVariable String name) {
        return ResponseEntity.ok(new BundleDto(bundleService.findById(name),
                handlerService.findAllByBundleId(name)));
    }

    /**
     * Removes a bundle from the catalog by deleting it from the database.
     * Only users with the "ROLE_WEB" role are allowed to access this endpoint.
     *
     * @param bundleId the ID of the bundle to unregister
     * @return a ResponseEntity indicating the success of the unregister process
     */
    @DeleteMapping(value = "/{bundleId}")
    @Secured("ROLE_WEB")
    public ResponseEntity<?> unregisterBundle(@PathVariable String bundleId) {
        var bundle = bundleService.findById(bundleId);
        Assert.isTrue(bundle != null, "error.bundle.is.null");
        bundleService.unregister(bundleId);
        return ResponseEntity.ok().build();
    }
}
