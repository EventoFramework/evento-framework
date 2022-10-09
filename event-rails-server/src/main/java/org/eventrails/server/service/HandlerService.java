package org.eventrails.server.service;

import org.eventrails.parser.model.handler.EventHandler;
import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.model.Ranch;
import org.eventrails.server.domain.model.types.HandlerType;
import org.eventrails.server.domain.repository.HandlerRepository;
import org.jgroups.Address;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class HandlerService {
	private final HandlerRepository handlerRepository;

	public HandlerService(HandlerRepository handlerRepository) {
		this.handlerRepository = handlerRepository;
	}

	public Handler findByPayloadName(String payloadName) {
		return handlerRepository.findByHandledPayload_Name(payloadName);
	}

	public Collection<Handler> findAllByPayloadName(String payloadName) {
		return handlerRepository.findAllByHandledPayload_Name(payloadName);
	}

	public Collection<Handler> findAllByRanchAndPayloadName(Ranch ranch, String payloadName) {
		return handlerRepository.findAllByRanchAndHandledPayload_Name(ranch, payloadName);
	}

	public Collection<Handler> findAllEventHandlersByRanch(Ranch ranch) {
		return handlerRepository.findAllByRanchAndHandlerTypeIn(ranch, List.of(HandlerType.EventHandler, HandlerType.SagaEventHandler));
	}


	public Handler save(Handler handler) {
		return handlerRepository.save(handler);
	}

	public boolean hasRanchHandlersForPayload(Ranch ranch, String payloadName){
		return handlerRepository.existsByRanchAndHandledPayload_NameAndHandlerType(ranch, payloadName, HandlerType.EventHandler);
	}
}
