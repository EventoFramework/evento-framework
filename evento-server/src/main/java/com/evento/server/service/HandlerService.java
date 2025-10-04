package com.evento.server.service;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.server.domain.model.core.Bundle;
import com.evento.server.domain.model.core.Handler;
import com.evento.server.domain.repository.core.HandlerRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
		return handlerRepository.findAllByComponent_BundleAndHandledPayload_Name(bundle, payloadName);
	}

	public Collection<Handler> findAllEventHandlersByBundle(Bundle bundle) {
		return handlerRepository.findAllByComponent_BundleAndHandlerTypeIn(bundle, List.of(HandlerType.EventHandler, HandlerType.SagaEventHandler));
	}


	public Handler save(Handler handler) {
		return handlerRepository.save(handler);
	}

	public boolean hasBundleHandlersForPayload(Bundle bundle, String payloadName) {
		return handlerRepository.existsByComponent_BundleAndHandledPayload_NameAndHandlerType(bundle, payloadName, HandlerType.EventHandler);
	}

	public List<Handler> findAll() {
		return handlerRepository.findAll();
	}

	public List<Handler> findAllByBundleId(String name) {
		return handlerRepository.findAllByComponent_Bundle_Id(name);
	}

	public Optional<Handler> findById(String handlerId) {
		return handlerRepository.findById(handlerId);
	}

    private final Map<String, List<String>> handlerCache = new ConcurrentHashMap<String, List<String> >();

	public List<String> findAllHandledPayloadsNameByComponentName(String componentName) {
        return handlerCache.computeIfAbsent(componentName, handlerRepository::findAllHandledPayloadsNameByComponentName);
	}

    public void clearCache(String componentName) {
        handlerCache.remove(componentName);
    }

    public void deleteAll(List<Handler> handlers) {

        handlerRepository.deleteAll(handlers);
    }

    public boolean exists(String bundleId, ComponentType componentType, String componentName, HandlerType handlerType, String handledPayload) {
        return handlerRepository.exists(bundleId, componentType, componentName, handlerType, handledPayload);
    }

    public void delete(Handler handler) {
        handlerRepository.delete(handler);
    }
}
