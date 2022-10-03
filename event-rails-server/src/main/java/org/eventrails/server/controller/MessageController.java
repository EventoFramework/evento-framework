package org.eventrails.server.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eventrails.server.service.MessageGateway;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller("/api/message")
public class MessageController {

	private final MessageGateway messageGateway;

	public MessageController(MessageGateway messageGateway) {
		this.messageGateway = messageGateway;
	}


	@PostMapping("/handle")
	public void handle(@RequestBody String commandPayload, ObjectMapper objectMapper) throws JsonProcessingException {

		messageGateway.handle(commandPayload);
	}
}
