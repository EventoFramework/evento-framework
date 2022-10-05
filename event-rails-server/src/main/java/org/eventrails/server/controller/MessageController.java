package org.eventrails.server.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eventrails.server.service.MessageGateway;
import org.eventrails.shared.ObjectMapperUtils;
import org.eventrails.shared.exceptions.ThrowableWrapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Mono;

@Controller()
public class MessageController {

	private final MessageGateway messageGateway;

	private final ObjectMapper payloadMapper = ObjectMapperUtils.getPayloadObjectMapper();

	public MessageController(MessageGateway messageGateway) {
		this.messageGateway = messageGateway;
	}


	@PostMapping(value = "/handle", produces = "application/json")
	public Mono<ResponseEntity<String>> handle(@RequestBody String message) throws JsonProcessingException {
		try
		{
			var resp = messageGateway.handle(message);
			return resp.map(ResponseEntity::ok);
		}catch (Throwable e){
			return Mono.just(ResponseEntity.status(500).body(payloadMapper.writeValueAsString(new ThrowableWrapper(
					e.getClass(),
					e.getMessage(),
					e.getStackTrace()
			))));
		}
	}
}
