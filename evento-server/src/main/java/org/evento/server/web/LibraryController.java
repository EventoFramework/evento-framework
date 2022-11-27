package org.evento.server.web;

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
	public ResponseEntity<List<PayloadDto>> findAll(){
		return ResponseEntity.ok(payloadRepository.findAll().stream().map(p ->
			new PayloadDto(p.getName(),
					p.getHandlers().stream().map(h ->
							new PayloadDto.HandlerDto(h.getUuid(),h.getBundle().getId(),
									h.getComponentName(),
									h.getReturnType() == null ? null : h.getReturnType().getName(),
									h.getComponentType(),  h.getHandlerType(),
									h.isReturnIsMultiple() ))
							.collect(Collectors.toList()),
					p.getType(), p.getJsonSchema())
		).collect(Collectors.toList()));
	}
}
