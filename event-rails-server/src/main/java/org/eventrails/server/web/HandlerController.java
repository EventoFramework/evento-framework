package org.eventrails.server.web;

import org.eventrails.server.service.HandlerService;
import org.eventrails.server.service.performance.ApplicationPetriNetService;
import org.eventrails.server.web.dto.HandlerDto;
import org.eventrails.server.web.dto.performance.NetworkDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api/handler")
public class HandlerController {

	private final HandlerService handlerService;

	private final ApplicationPetriNetService applicationPetriNetService;

	public HandlerController(HandlerService handlerService, ApplicationPetriNetService applicationPetriNetService) {
		this.handlerService = handlerService;
		this.applicationPetriNetService = applicationPetriNetService;
	}

	@GetMapping("/")
	public ResponseEntity<List<HandlerDto>> findAllHandlers(){
		return ResponseEntity.ok(handlerService.findAll().stream().map(HandlerDto::new).collect(Collectors.toList()));
	}

	@GetMapping("/to-petri-net")
	public ResponseEntity<NetworkDto> toPetriNet(){
		return ResponseEntity.ok(new NetworkDto(applicationPetriNetService.toPetriNet()));
	}
}
