package org.evento.server.web;

import org.evento.server.domain.repository.ComponentRepository;
import org.evento.server.domain.repository.HandlerRepository;
import org.evento.server.domain.repository.PayloadRepository;
import org.evento.server.domain.repository.projection.ComponentListProjection;
import org.evento.server.domain.repository.projection.PayloadListProjection;
import org.evento.server.domain.repository.projection.PayloadProjection;
import org.evento.server.web.dto.ComponentDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/catalog")
@Secured("ROLE_WEB")
public class CatalogController {

	private final PayloadRepository payloadRepository;

	private final ComponentRepository componentRepository;

	private final HandlerRepository handlerRepository;

	public CatalogController(PayloadRepository payloadRepository, ComponentRepository handlerRepository, HandlerRepository handlerRepository1) {
		this.payloadRepository = payloadRepository;
		this.componentRepository = handlerRepository;
		this.handlerRepository = handlerRepository1;
	}

	@GetMapping(value = "/payload/", produces = "application/json")
	public ResponseEntity<List<PayloadListProjection>> findAll() {
		return ResponseEntity.ok(payloadRepository.findAllProjection());
	}

	@GetMapping(value = "/payload/{payloadName}", produces = "application/json")
	public ResponseEntity<PayloadProjection> findByName(@PathVariable String payloadName) {
		return ResponseEntity.ok(payloadRepository.findByIdProjection(payloadName).orElseThrow());
	}

	@GetMapping(value = "/component/", produces = "application/json")
	public ResponseEntity<List<ComponentListProjection>> findAllComponents() {
		return ResponseEntity.ok(componentRepository.findAllComponentProjection());
	}

	@GetMapping(value = "/component/{componentName}", produces = "application/json")
	public ResponseEntity<ComponentDTO> findComponentByName(@PathVariable String componentName) {
		return componentRepository.findById(componentName)
				.map(c -> ResponseEntity.ok(new ComponentDTO(c, handlerRepository.findAllByComponent(c))))
				.orElseThrow();
	}

}
