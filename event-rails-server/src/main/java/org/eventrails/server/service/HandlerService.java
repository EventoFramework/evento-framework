package org.eventrails.server.service;

import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.repository.HandlerRepository;
import org.springframework.stereotype.Service;

@Service
public class HandlerService {
	private final HandlerRepository handlerRepository;

	public HandlerService(HandlerRepository handlerRepository) {
		this.handlerRepository = handlerRepository;
	}

	public Handler findByPayloadName(String commandName) {
		return handlerRepository.findByHandledPayload_Name(commandName);
	}
}
