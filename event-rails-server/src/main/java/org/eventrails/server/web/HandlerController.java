package org.eventrails.server.web;

import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.service.HandlerService;
import org.eventrails.server.web.dto.HandlerDto;
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

	public HandlerController(HandlerService handlerService) {
		this.handlerService = handlerService;
	}

	@GetMapping("/")
	public ResponseEntity<List<HandlerDto>> findAllHandlers(){
		return ResponseEntity.ok(handlerService.findAll().stream().map(HandlerDto::new).collect(Collectors.toList()));
	}
}