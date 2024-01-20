package org.evento.server.web;

import org.evento.server.domain.repository.core.ComponentRepository;
import org.evento.server.domain.repository.core.HandlerRepository;
import org.evento.server.domain.repository.core.PayloadRepository;
import org.evento.server.domain.repository.core.projection.ComponentListProjection;
import org.evento.server.domain.repository.core.projection.PayloadListProjection;
import org.evento.server.domain.repository.core.projection.PayloadProjection;
import org.evento.server.web.dto.ComponentDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * This class is a REST controller that handles the catalog related API endpoints.
 * It requires the user to have the "ROLE_WEB" role for accessing any of its methods.
 */
@RestController
@RequestMapping("api/catalog")
@Secured("ROLE_WEB")
public class CatalogController {

	private final PayloadRepository payloadRepository;

	private final ComponentRepository componentRepository;

	private final HandlerRepository handlerRepository;

	/**
	 * This class is a REST controller that handles the catalog related API endpoints.
	 * It requires the user to have the "ROLE_WEB" role for accessing any of its methods.
	 */
	public CatalogController(PayloadRepository payloadRepository, ComponentRepository handlerRepository, HandlerRepository handlerRepository1) {
		this.payloadRepository = payloadRepository;
		this.componentRepository = handlerRepository;
		this.handlerRepository = handlerRepository1;
	}

	/**
	 * Retrieves a list of Payloads using the findAllProjection method of the PayloadRepository.
	 * This method queries the database and returns a list of objects that implement the PayloadListProjection interface.
	 *
	 * @return ResponseEntity<List < PayloadListProjection>> - The response entity containing the list of payloads.
	 * @see PayloadListProjection
	 * @see PayloadRepository#findAllProjection()
	 */
	@GetMapping(value = "/payload/", produces = "application/json")
	public ResponseEntity<List<PayloadListProjection>> findAll() {
		return ResponseEntity.ok(payloadRepository.findAllProjection());
	}

	/**
	 * Retrieves a Payload object by its name.
	 *
	 * @param payloadName The name of the payload to retrieve.
	 * @return ResponseEntity<PayloadProjection> - The response entity containing the payload.
	 */
	@GetMapping(value = "/payload/{payloadName}", produces = "application/json")
	public ResponseEntity<PayloadProjection> findByName(@PathVariable String payloadName) {
		return ResponseEntity.ok(payloadRepository.findByIdProjection(payloadName).orElseThrow());
	}

	/**
	 * Retrieves a list of all components in the catalog.
	 *
	 * @return ResponseEntity<List < ComponentListProjection>> - The response entity containing the list of components.
	 */
	@GetMapping(value = "/component/", produces = "application/json")
	public ResponseEntity<List<ComponentListProjection>> findAllComponents() {
		return ResponseEntity.ok(componentRepository.findAllComponentProjection());
	}

	/**
	 * Retrieves a component by its name.
	 *
	 * @param componentName The name of the component to retrieve.
	 * @return ResponseEntity<ComponentDTO> - The response entity containing the component.
	 * @throws java.util.NoSuchElementException if the component with the given name does not exist.
	 */
	@GetMapping(value = "/component/{componentName}", produces = "application/json")
	public ResponseEntity<ComponentDTO> findComponentByName(@PathVariable String componentName) {
		return componentRepository.findById(componentName)
				.map(c -> ResponseEntity.ok(new ComponentDTO(c, handlerRepository.findAllByComponent(c))))
				.orElseThrow();
	}

}
