package org.eventrails.server.service;

import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.model.Bundle;
import org.eventrails.common.modeling.bundle.types.HandlerType;
import org.eventrails.server.domain.repository.HandlerRepository;
import org.springframework.stereotype.Service;

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

	public Collection<Handler> findAllByBundleAndPayloadName(Bundle bundle, String payloadName) {
		return handlerRepository.findAllByBundleAndHandledPayload_Name(bundle, payloadName);
	}

	public Collection<Handler> findAllEventHandlersByBundle(Bundle bundle) {
		return handlerRepository.findAllByBundleAndHandlerTypeIn(bundle, List.of(HandlerType.EventHandler, HandlerType.SagaEventHandler));
	}


	public Handler save(Handler handler) {
		return handlerRepository.save(handler);
	}

	public boolean hasBundleHandlersForPayload(Bundle bundle, String payloadName){
		return handlerRepository.existsByBundleAndHandledPayload_NameAndHandlerType(bundle, payloadName, HandlerType.EventHandler);
	}

	public List<Handler> findAll() {
		return handlerRepository.findAll();
	}

	public List<Handler> findAllByBundleName(String name) {
		return handlerRepository.findAllByBundle_Name(name);
	}
}
