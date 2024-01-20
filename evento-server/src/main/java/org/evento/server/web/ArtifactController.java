package org.evento.server.web;

import org.evento.server.service.BundleService;
import org.springframework.security.access.annotation.Secured;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ArtifactController handles requests related to artifacts.
 */
@RestController
@RequestMapping("asset")
public class ArtifactController {

	private final BundleService bundleService;

	/**
	 * The ArtifactController class handles requests related to artifacts.
	 */
	public ArtifactController(BundleService bundleService) {
		this.bundleService = bundleService;
	}

	/**
	 * Retrieves the artifact for a given bundle ID.
	 *
	 * @param bundleId The ID of the bundle.
	 * @return The artifact as a byte array.
	 * @throws IOException If an I/O error occurs.
	 * @throws IllegalArgumentException If the bundle is not found.
	 */
	@GetMapping(value = "/bundle/{bundleId}", produces = "application/zip")
	@Secured("ROLE_DEPLOY")
	public @ResponseBody byte[] getBundleArtifact(@PathVariable String bundleId) throws IOException {
		var bundle = bundleService.findById(bundleId);
		Assert.isTrue(bundle != null, "error.bundle.not.found");
		//noinspection SwitchStatementWithTooFewBranches
		return switch (bundle.getBucketType())
		{
			default -> Files.readAllBytes(Path.of(bundle.getArtifactCoordinates()));
		};
	}
}
