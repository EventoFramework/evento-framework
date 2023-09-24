package org.evento.server.web;

import org.evento.server.service.performance.ApplicationPerformanceModelService;
import org.evento.server.web.dto.performance.PerformanceModelDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/flows")
public class FlowsController {


	private final ApplicationPerformanceModelService applicationPerformanceModelService;

	public FlowsController(ApplicationPerformanceModelService applicationPerformanceModelService) {
		this.applicationPerformanceModelService = applicationPerformanceModelService;
	}


	@GetMapping("/")
	@Secured("ROLE_WEB")
	public ResponseEntity<PerformanceModelDTO> toPerformanceModel() {
		return ResponseEntity.ok(new PerformanceModelDTO(applicationPerformanceModelService.toPerformanceModel()));
	}

	@GetMapping("/handler/{handlerId}")
	@Secured("ROLE_WEB")
	public ResponseEntity<PerformanceModelDTO> toPerformanceModel(@PathVariable String handlerId) {
		return ResponseEntity.ok(new PerformanceModelDTO(applicationPerformanceModelService.toPerformanceModel(handlerId)));
	}

	@GetMapping("/payload/{payload}")
	@Secured("ROLE_WEB")
	public ResponseEntity<PerformanceModelDTO> toPerformanceModelFromPayload(@PathVariable String payload) {
		return ResponseEntity.ok(new PerformanceModelDTO(applicationPerformanceModelService.toPerformanceModelFromPayload(payload)));
	}

	@GetMapping("/component/{component}")
	@Secured("ROLE_WEB")
	public ResponseEntity<PerformanceModelDTO> toPerformanceModelFromComponent(@PathVariable String component) {
		return ResponseEntity.ok(new PerformanceModelDTO(applicationPerformanceModelService.toPerformanceModelFromComponent(component)));
	}

	@GetMapping("/bundle/{bundle}")
	@Secured("ROLE_WEB")
	public ResponseEntity<PerformanceModelDTO> toPerformanceModelFromBundle(@PathVariable String bundle) {
		return ResponseEntity.ok(new PerformanceModelDTO(applicationPerformanceModelService.toPerformanceModelFromBundle(bundle)));
	}
}
