package com.evento.server.web;

import com.evento.server.domain.model.core.Component;
import com.evento.server.domain.repository.core.ComponentRepository;
import com.evento.server.domain.repository.core.HandlerRepository;
import com.evento.server.domain.repository.core.PayloadRepository;
import com.evento.server.domain.repository.core.projection.ComponentListProjection;
import com.evento.server.domain.repository.core.projection.PayloadListProjection;
import com.evento.server.domain.repository.core.projection.PayloadProjection;
import com.evento.server.web.dto.ComponentDTO;
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
	 * @return ResponseEntity - The response entity containing the list of payloads.
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
	 * @return ResponseEntity - The response entity containing the payload.
	 */
	@GetMapping(value = "/payload/{payloadName}", produces = "application/json")
	public ResponseEntity<PayloadProjection> findByName(@PathVariable String payloadName) {
		return ResponseEntity.ok(payloadRepository.findByIdProjection(payloadName).orElseThrow());
	}

	/**
	 * Retrieves a list of all components in the catalog.
	 *
	 * @return ResponseEntity - The response entity containing the list of components.
	 */
	@GetMapping(value = "/component/", produces = "application/json")
	public ResponseEntity<List<ComponentListProjection>> findAllComponents() {
		return ResponseEntity.ok(componentRepository.findAllComponentProjection());
	}

	/**
	 * Retrieves a component by its catalog id ({@code bundleId:componentName}) or, for links
	 * that predate per-bundle component keys, by its bare name.
	 *
	 * <p>A bare name resolves only when exactly one bundle has a component of that name; when
	 * several do (the same class deployed in several bundles) the answer is {@code 409 Conflict}
	 * listing the candidate ids, rather than an arbitrary one of them.
	 *
	 * @param identifier The component id, or the bare name of the component to retrieve.
	 * @return ResponseEntity - The response entity containing the component.
	 * @throws java.util.NoSuchElementException if no such component exists.
	 */
	@GetMapping(value = "/component/{identifier}", produces = "application/json")
	public ResponseEntity<?> findComponentByName(@PathVariable String identifier) {
		var byId = componentRepository.findById(identifier);
		if (byId.isPresent()) {
			var c = byId.get();
			return ResponseEntity.ok(new ComponentDTO(c, handlerRepository.findAllByComponent(c)));
		}
		var byName = componentRepository.findAllByComponentNameOrderByBundle_Id(identifier);
		if (byName.isEmpty()) throw new java.util.NoSuchElementException("No component " + identifier);
		if (byName.size() > 1) {
			return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(java.util.Map.of(
					"error", "component name '" + identifier + "' exists in several bundles; use its id",
					"candidates", byName.stream().map(Component::getId).toList()));
		}
		var c = byName.getFirst();
		return ResponseEntity.ok(new ComponentDTO(c, handlerRepository.findAllByComponent(c)));
	}

}
