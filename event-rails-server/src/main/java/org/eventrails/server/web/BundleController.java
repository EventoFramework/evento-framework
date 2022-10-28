package org.eventrails.server.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eventrails.modeling.utils.ObjectMapperUtils;
import org.eventrails.parser.model.BundleDescription;
import org.eventrails.server.domain.model.BucketType;
import org.eventrails.server.domain.model.Bundle;
import org.eventrails.server.service.BundleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
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

	public BundleController(BundleService bundleService) {
		this.bundleService = bundleService;
	}


	@GetMapping(value = "/", produces = "application/json")
	public ResponseEntity<List<Bundle>> findAll(){
		return ResponseEntity.ok(bundleService.findAllBundles());
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
	public ResponseEntity<?> unregisterBundle(@PathVariable String bundleName) throws JsonProcessingException {
		bundleService.unregister(bundleName);
		return ResponseEntity.ok().build();
	}
}
