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
@RequestMapping("api/handler")
public class HandlerController {

	private final HandlerService handlerService;

	private final ApplicationQueueNetService applicationQueueNetService;

	public HandlerController(HandlerService handlerService, ApplicationQueueNetService applicationQueueNetService) {
		this.handlerService = handlerService;
		this.applicationQueueNetService = applicationQueueNetService;
	}

	@GetMapping("/")
	public ResponseEntity<List<HandlerDto>> findAllHandlers(){
		return ResponseEntity.ok(handlerService.findAll().stream().map(HandlerDto::new).collect(Collectors.toList()));
	}

	@GetMapping("/to-queue-net")
	public ResponseEntity<QueueNetworkDTO> toQueueNet(){
		return ResponseEntity.ok(new QueueNetworkDTO(applicationQueueNetService.toQueueNetwork()));
	}

	@GetMapping("/to-queue-net/{handlerId}")
	public ResponseEntity<QueueNetworkDTO> toQueueNet(@PathVariable String handlerId){
		return ResponseEntity.ok(new QueueNetworkDTO(applicationQueueNetService.toQueueNetwork(handlerId)));
	}
}
