package org.eventrails.server.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eventrails.modeling.utils.ObjectMapperUtils;
import org.eventrails.parser.RanchApplicationParser;
import org.eventrails.parser.model.RanchApplicationDescription;
import org.eventrails.server.domain.model.BucketType;
import org.eventrails.server.domain.model.Ranch;
import org.eventrails.server.service.RanchApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("api/ranch")
public class RanchController {

	@Value("${file.upload-dir}")
	private String fileUploadDir;

	private final RanchApplicationService ranchApplicationService;

	public RanchController(RanchApplicationService ranchApplicationService) {
		this.ranchApplicationService = ranchApplicationService;
	}


	@GetMapping(value = "/", produces = "application/json")
	public ResponseEntity<List<Ranch>> findAll(){
		return ResponseEntity.ok(ranchApplicationService.findAllRanches());
	}

	@PostMapping(value = "/", produces = "application/json")
	public ResponseEntity<?> registerRanch(@RequestParam("ranch") MultipartFile ranch) throws IOException {

		ZipInputStream zis = new ZipInputStream(ranch.getInputStream());
		String ranchName = ranch.getOriginalFilename().replace(".ranch", "");

		var jarEntry = zis.getNextEntry();
		var jarUploadPath = fileUploadDir + "/" + ranchName + "-" + Instant.now().toEpochMilli() + ".jar";

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

			ranchApplicationService.register(
					ranchName,
					BucketType.LocalFilesystem,
					jarUploadPath,
					jarEntry.getName(),
					ObjectMapperUtils.getPayloadObjectMapper().readValue(bos.toByteArray(), RanchApplicationDescription.class));

		}
		return ResponseEntity.ok().build();
	}

	@DeleteMapping(value = "/{ranchName}")
	public ResponseEntity<?> unregisterRanch(@PathVariable String ranchName) throws JsonProcessingException {
		ranchApplicationService.unregister(ranchName);
		return ResponseEntity.ok().build();
	}
}
