package org.eventrails.server.web;

import org.eventrails.common.serialization.ObjectMapperUtils;
import org.eventrails.parser.model.BundleDescription;
import org.eventrails.server.domain.model.BucketType;
import org.eventrails.server.domain.model.Bundle;
import org.eventrails.server.service.BundleService;
import org.eventrails.server.service.HandlerService;
import org.eventrails.server.web.dto.BundleDto;
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
				handlerService.findAllByBundleName(name)));
	}

	@PostMapping(value = "/", produces = "application/json")
	public ResponseEntity<?> registerBundle(@RequestParam("bundle") MultipartFile bundle) throws IOException {

		ZipInputStream zis = new ZipInputStream(bundle.getInputStream());
		String bundleName = bundle.getOriginalFilename().replace(".bundle", "");

		var jarEntry = zis.getNextEntry();
		var jarUploadPath = fileUploadDir + "/" + bundleName + "-" + Instant.now().toEpochMilli() + ".jar";

		byte[] buffer = new byte[2048];
		try (FileOutputStream fos = new FileOutputStream(jarUploadPath);
			 BufferedOutputStream bos = new BufferedOutputStream(fos, buffer.length)) {

			int len;
			while ((len = zis.read(buffer)) > 0) {
				bos.write(buffer, 0, len);
			}
		}

		zis.getNextEntry();
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream(buffer.length)) {

			int len;
			while ((len = zis.read(buffer)) > 0) {
				bos.write(buffer, 0, len);
			}

			bundleService.register(
					bundleName,
					BucketType.LocalFilesystem,
					jarUploadPath,
					jarEntry.getName(),
					ObjectMapperUtils.getPayloadObjectMapper().readValue(bos.toByteArray(), BundleDescription.class));

		}
		return ResponseEntity.ok().build();
	}

	@DeleteMapping(value = "/{bundleName}")
	public ResponseEntity<?> unregisterBundle(@PathVariable String bundleName) {
		var bundle = bundleService.findByName(bundleName);
		Assert.isTrue(bundle != null, "error.bundle.is.null");
		Assert.isTrue(bundle.getBucketType() != BucketType.Ephemeral, "error.bundle.is.epehemeral");
		bundleService.unregister(bundleName);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/{bundleName}/env/{key}")
	public ResponseEntity<?> putEnv(@PathVariable String bundleName, @PathVariable String key, @RequestBody String value) {
		bundleService.putEnv(bundleName, key, value);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/{bundleName}/env/{key}")
	public ResponseEntity<?> removeEnv(@PathVariable String bundleName, @PathVariable String key){
		bundleService.removeEnv(bundleName, key);
		return ResponseEntity.ok().build();
	}
	@PostMapping("/{bundleName}/vm-option/{key}")
	public ResponseEntity<?> putVmOption(@PathVariable String bundleName, @PathVariable String key, @RequestBody String value) {
		bundleService.putVmOption(bundleName, key, value);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/{bundleName}/vm-option/{key}")
	public ResponseEntity<?> removeVmOption(@PathVariable String bundleName, @PathVariable String key){
		bundleService.removeVmOption(bundleName, key);
		return ResponseEntity.ok().build();
	}
}
