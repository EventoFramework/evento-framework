package org.evento.server.web;

import org.evento.server.domain.model.Payload;
import org.evento.server.domain.repository.PayloadListProjection;
import org.evento.server.domain.repository.PayloadProjection;
import org.evento.server.domain.repository.PayloadRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/catalog")
public class CatalogController {

	private final PayloadRepository payloadRepository;

	public CatalogController(PayloadRepository payloadRepository) {
		this.payloadRepository = payloadRepository;
	}

	@GetMapping(value = "/", produces = "application/json")
	public ResponseEntity<List<PayloadListProjection>> findAll(){
		return ResponseEntity.ok(payloadRepository.findAllProjection());
	}
	@GetMapping(value = "/{payloadName}", produces = "application/json")
	public ResponseEntity<PayloadProjection> findByName(@PathVariable String payloadName){
		return ResponseEntity.ok(payloadRepository.findByIdProjection(payloadName).orElseThrow());
	}
}
