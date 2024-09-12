package com.evento.server.web;

import com.evento.common.serialization.ObjectMapperUtils;
import com.evento.parser.model.BundleDescription;
import com.evento.server.domain.model.core.BucketType;
import com.evento.server.domain.repository.core.projection.BundleListProjection;
import com.evento.server.service.BundleService;
import com.evento.server.service.HandlerService;
import com.evento.server.web.dto.BundleDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipInputStream;

/**
 * The {@code BundleController} class is a controller class that handles HTTP requests for managing bundles.
 * It provides methods for retrieving bundles, registering and unregistering bundles, and managing environment
 * variables and VM options for bundles.
 */
@RestController
@RequestMapping("api/bundle")
public class BundleController {

    private final BundleService bundleService;
    private final HandlerService handlerService;
    @Value("${evento.file.upload-dir}")
    private String fileUploadDir;

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
     * Registers a bundle.
     * <p>
     * This method is used to register a bundle by saving it to the file system and storing its information in the database.
     * Only users with the "ROLE_PUBLISH" role are allowed to access this endpoint.
     *
     * @param bundle The MultipartFile containing the bundle file.
     * @return A ResponseEntity representing the success of the registration process.
     * @throws IOException Thrown if there is an error reading the bundle file.
     */
    @PostMapping(value = "/", produces = "application/json")
    @Secured("ROLE_PUBLISH")
    public ResponseEntity<?> registerBundle(@RequestParam("bundle") MultipartFile bundle) throws IOException {

        ZipInputStream zis = new ZipInputStream(bundle.getInputStream());
        String bundleId = Objects.requireNonNull(bundle.getOriginalFilename()).replace(".bundle", "");

        byte[] buffer = new byte[2048];

        zis.getNextEntry();
        ByteArrayOutputStream b = new ByteArrayOutputStream(buffer.length);
        int len;
        while ((len = zis.read(buffer)) > 0) {
            b.write(buffer, 0, len);
        }
        var bundleDescription = ObjectMapperUtils.getPayloadObjectMapper().readValue(b.toByteArray(), BundleDescription.class);

        var jarName = bundleId + ".jar";
        var jarUploadPath = "-";
        if (bundleDescription.getDeployable()) {

            var jarEntry = zis.getNextEntry();
            jarName = Objects.requireNonNull(jarEntry).getName();
            jarUploadPath = fileUploadDir + "/" + bundleId + "-" + Instant.now().toEpochMilli() + ".jar";
            try (FileOutputStream fos = new FileOutputStream(jarUploadPath);
                 BufferedOutputStream bos = new BufferedOutputStream(fos, buffer.length)) {
                while ((len = zis.read(buffer)) > 0) {
                    bos.write(buffer, 0, len);
                }
            }
        }

        bundleService.register(
                bundleId,
                bundleDescription.getDeployable() ? BucketType.LocalFilesystem : BucketType.None,
                jarUploadPath,
                jarName,
                bundleDescription);


        return ResponseEntity.ok().build();
    }

    /**
     * Unregisters a bundle by deleting it from the file system and database.
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
        Assert.isTrue(bundle.getBucketType() != BucketType.Ephemeral, "error.bundle.is.epehemeral");
        bundleService.unregister(bundleId);
        return ResponseEntity.ok().build();
    }

    /**
     * Updates the environment variable value for a given bundle.
     * <p>
     * This method is used to update the value of an environment variable associated with a specific bundle.
     * The user must have the "ROLE_WEB" role to access this endpoint.
     *
     * @param bundleId the ID of the bundle to update the environment variable for
     * @param key      the key of the environment variable
     * @param value    the new value of the environment variable
     * @return a ResponseEntity indicating the success of the update process
     */
    @PostMapping("/{bundleId}/env/{key}")
    @Secured("ROLE_WEB")
    public ResponseEntity<?> putEnv(@PathVariable String bundleId, @PathVariable String key, @RequestBody String value) {
        bundleService.putEnv(bundleId, key, value);
        return ResponseEntity.ok().build();
    }

    /**
     * Removes an environment variable for a given bundle.
     * <p>
     * This method is used to remove an environment variable associated with a specific bundle.
     * The user must have the "ROLE_WEB" role to access this endpoint.
     *
     * @param bundleId the ID of the bundle to remove the environment variable for
     * @param key      the key of the environment variable to remove
     * @return a ResponseEntity indicating the success of the removal process
     */
    @DeleteMapping("/{bundleId}/env/{key}")
    @Secured("ROLE_WEB")
    public ResponseEntity<?> removeEnv(@PathVariable String bundleId, @PathVariable String key) {
        bundleService.removeEnv(bundleId, key);
        return ResponseEntity.ok().build();
    }

    /**
     * Updates the value of a virtual machine (VM) option for a given bundle.
     *
     * <p>
     * This method is used to update the value of a specific VM option associated with a bundle.
     * The user must have the "ROLE_WEB" role to access this endpoint.
     * </p>
     *
     * @param bundleId the ID of the bundle to update the VM option for
     * @param key      the key of the VM option
     * @param value    the new value of the VM option
     * @return a ResponseEntity indicating the success of the update process
     */
    @PostMapping("/{bundleId}/vm-option/{key}")
    @Secured("ROLE_WEB")
    public ResponseEntity<?> putVmOption(@PathVariable String bundleId, @PathVariable String key, @RequestBody String value) {
        bundleService.putVmOption(bundleId, key, value);
        return ResponseEntity.ok().build();
    }

    /**
     * Removes a virtual machine (VM) option for a given bundle.
     *
     * <p>
     * This method is used to remove a specific VM option associated with a bundle.
     * The user must have the "ROLE_WEB" role to access this endpoint.
     * </p>
     *
     * @param bundleId the ID of the bundle to remove the VM option for
     * @param key      the key of the VM option to remove
     * @return a ResponseEntity indicating the success of the removal process
     */
    @DeleteMapping("/{bundleId}/vm-option/{key}")
    @Secured("ROLE_WEB")
    public ResponseEntity<?> removeVmOption(@PathVariable String bundleId, @PathVariable String key) {
        bundleService.removeVmOption(bundleId, key);
        return ResponseEntity.ok().build();
    }
}
