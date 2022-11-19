package org.eventrails.server.web;

import org.apache.commons.io.IOUtils;
import org.eventrails.server.service.BundleService;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import javax.swing.text.Caret;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("asset")
public class ArtifactController {

	private final BundleService bundleService;

	public ArtifactController(BundleService bundleService) {
		this.bundleService = bundleService;
	}

	@GetMapping(value = "/bundle/{bundleId}", produces = "application/zip")
	public @ResponseBody byte[] getBundleArtifact(@PathVariable String bundleId) throws IOException {
		var bundle = bundleService.findByName(bundleId);
		Assert.isTrue(bundle != null, "error.bundle.not.found");
		return switch (bundle.getBucketType()){
			default -> Files.readAllBytes(Path.of(bundle.getArtifactCoordinates()));
		};
	}
}
