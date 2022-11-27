package org.evento.server.web;

import org.evento.common.serialization.ObjectMapperUtils;
import org.evento.parser.model.BundleDescription;
import org.evento.server.service.BundleService;
import org.evento.server.service.HandlerService;
import org.evento.server.web.dto.BundleDto;
import org.evento.server.domain.model.BucketType;
import org.evento.server.domain.model.Bundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("api/bundle")
public class BundleController {

	@Value("${file.upload-dir}")
	private String fileUploadDir;

	private final BundleService bundleService;
	private final HandlerService handlerService;

	public BundleController(BundleService bundleService, HandlerService handlerService) {
		this.bundleService = bundleService;
		this.handlerService = handlerService;
	}


	@GetMapping(value = "/", produces = "application/json")
	public ResponseEntity<List<Bundle>> findAll(){
		return ResponseEntity.ok(bundleService.findAllBundles());
	}
	@GetMapping(value = "/{name}", produces = "application/json")
	public ResponseEntity<BundleDto> findByName(@PathVariable String name){
		return ResponseEntity.ok(new BundleDto(bundleService.findByName(name),
				handlerService.findAllByBundleId(name)));
	}

	@PostMapping(value = "/", produces = "application/json")
	public ResponseEntity<?> registerBundle(@RequestParam("bundle") MultipartFile bundle) throws IOException {

		ZipInputStream zis = new ZipInputStream(bundle.getInputStream());
		String bundleId = bundle.getOriginalFilename().replace(".bundle", "");

		var jarEntry = zis.getNextEntry();
		var jarUploadPath = fileUploadDir + "/" + bundleId + "-" + Instant.now().toEpochMilli() + ".jar";

		byte[] buffer = new byte[2048];
		try (FileOutputStream fos = new FileOutputStream(jarUploadPath);
			 BufferedOutputStream bos = new BufferedOutputStream(fos, buffer.length)) {

			int len;
			while ((len = zis.read(buffer)) > 0) {
				bos.write(buffer, 0, len);
			}
		}

		zis.getNextEntry();
		ByteArrayOutputStream bos = new ByteArrayOutputStream(buffer.length);

		int len;
		while ((len = zis.read(buffer)) > 0) {
			bos.write(buffer, 0, len);
		}

		bundleService.register(
				bundleId,
				BucketType.LocalFilesystem,
				jarUploadPath,
				jarEntry.getName(),
				ObjectMapperUtils.getPayloadObjectMapper().readValue(bos.toByteArray(), BundleDescription.class));


		return ResponseEntity.ok().build();
	}

	@DeleteMapping(value = "/{bundleId}")
	public ResponseEntity<?> unregisterBundle(@PathVariable String bundleId) {
		var bundle = bundleService.findByName(bundleId);
		Assert.isTrue(bundle != null, "error.bundle.is.null");
		Assert.isTrue(bundle.getBucketType() != BucketType.Ephemeral, "error.bundle.is.epehemeral");
		bundleService.unregister(bundleId);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/{bundleId}/env/{key}")
	public ResponseEntity<?> putEnv(@PathVariable String bundleId, @PathVariable String key, @RequestBody String value) {
		bundleService.putEnv(bundleId, key, value);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/{bundleId}/env/{key}")
	public ResponseEntity<?> removeEnv(@PathVariable String bundleId, @PathVariable String key){
		bundleService.removeEnv(bundleId, key);
		return ResponseEntity.ok().build();
	}
	@PostMapping("/{bundleId}/vm-option/{key}")
	public ResponseEntity<?> putVmOption(@PathVariable String bundleId, @PathVariable String key, @RequestBody String value) {
		bundleService.putVmOption(bundleId, key, value);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/{bundleId}/vm-option/{key}")
	public ResponseEntity<?> removeVmOption(@PathVariable String bundleId, @PathVariable String key){
		bundleService.removeVmOption(bundleId, key);
		return ResponseEntity.ok().build();
	}
}
