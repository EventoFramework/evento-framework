package org.evento.server.web;

import org.evento.server.domain.repository.PayloadProjection;
import org.evento.server.web.dto.PayloadDto;
import org.evento.server.domain.repository.PayloadRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api/library")
public class LibraryController {

	private final PayloadRepository payloadRepository;

	public LibraryController(PayloadRepository payloadRepository) {
		this.payloadRepository = payloadRepository;
	}

	@GetMapping(value = "/", produces = "application/json")
	public ResponseEntity<List<PayloadProjection>> findAll(){
		return ResponseEntity.ok(payloadRepository.findAllProjection());
	}
}
