package org.evento.server.web;

import org.evento.server.service.HandlerService;
import org.evento.server.service.performance.ApplicationQueueNetService;
import org.evento.server.web.dto.HandlerDto;
import org.evento.server.web.dto.performance.QueueNetworkDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api/flows")
public class FlowsController {


	private final ApplicationQueueNetService applicationQueueNetService;

	public FlowsController(ApplicationQueueNetService applicationQueueNetService) {
		this.applicationQueueNetService = applicationQueueNetService;
	}


	@GetMapping("/")
	public ResponseEntity<QueueNetworkDTO> toQueueNet(){
		return ResponseEntity.ok(new QueueNetworkDTO(applicationQueueNetService.toQueueNetwork()));
	}

	@GetMapping("/handler/{handlerId}")
	public ResponseEntity<QueueNetworkDTO> toQueueNet(@PathVariable String handlerId){
		return ResponseEntity.ok(new QueueNetworkDTO(applicationQueueNetService.toQueueNetwork(handlerId)));
	}

	@GetMapping("/payload/{payload}")
	public ResponseEntity<QueueNetworkDTO> toQueueNetFromPayload(@PathVariable String payload){
		return ResponseEntity.ok(new QueueNetworkDTO(applicationQueueNetService.toQueueNetworkFromPayload(payload)));
	}

	@GetMapping("/component/{component}")
	public ResponseEntity<QueueNetworkDTO> toQueueNetFromComponent(@PathVariable String component){
		return ResponseEntity.ok(new QueueNetworkDTO(applicationQueueNetService.toQueueNetworkFromComponent(component)));
	}

	@GetMapping("/bundle/{bundle}")
	public ResponseEntity<QueueNetworkDTO> toQueueNetFromBundle(@PathVariable String bundle){
		return ResponseEntity.ok(new QueueNetworkDTO(applicationQueueNetService.toQueueNetworkFromBundle(bundle)));
	}
}
