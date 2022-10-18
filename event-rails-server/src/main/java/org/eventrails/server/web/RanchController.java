package org.eventrails.server.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eventrails.modeling.utils.ObjectMapperUtils;
import org.eventrails.parser.model.RanchApplicationDescription;
import org.eventrails.server.domain.model.BucketType;
import org.eventrails.server.domain.model.Ranch;
import org.eventrails.server.service.RanchApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("api/ranch")
public class RanchController {

	private final RanchApplicationService ranchApplicationService;
	private final ObjectMapper objectMapper = ObjectMapperUtils.getResultObjectMapper();

	public RanchController(RanchApplicationService ranchApplicationService) {
		this.ranchApplicationService = ranchApplicationService;
	}


	@GetMapping(value = "/", produces = "application/json")
	public ResponseEntity<List<Ranch>> findAll(){
		return ResponseEntity.ok(ranchApplicationService.findAllRanches());
	}

	@PostMapping(value = "/", produces = "application/json")
	public ResponseEntity<?> registerRanch(@RequestParam("artifact") MultipartFile artifact,
										@RequestParam("ranch-name") String ranchName,
										@RequestParam("application-description-json") String ranchApplicationDescription) throws JsonProcessingException {
		ranchApplicationService.register(
				ranchName,
				BucketType.LocalFilesystem,
				"",
				objectMapper.readValue(ranchApplicationDescription, RanchApplicationDescription.class)
		);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping(value = "/{ranchName}")
	public ResponseEntity<?> unregisterRanch(@PathVariable String ranchName) throws JsonProcessingException {
		ranchApplicationService.unregister(ranchName);
		return ResponseEntity.ok().build();
	}
}
