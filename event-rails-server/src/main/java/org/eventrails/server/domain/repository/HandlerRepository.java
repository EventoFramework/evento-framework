package org.eventrails.server.domain.repository;

import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.model.Bundle;
import org.eventrails.server.domain.model.types.ComponentType;
import org.eventrails.server.domain.model.types.HandlerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface HandlerRepository extends JpaRepository<Handler, Bundle> {

	public void deleteAllByBundle_Name(String bundleName);

	public Handler findByHandledPayload_Name(String name);

	Collection<Handler> findAllByHandledPayload_Name(String payloadName);
	List<Handler> findAllByHandlerTypeOrderByUuid(HandlerType handlerType);

	boolean existsByBundleAndHandledPayload_NameAndHandlerType(Bundle bundle, String payloadName, HandlerType handlerType);
	List<Handler> findAllByBundleAndHandlerType(Bundle bundle, HandlerType handlerType);
	List<Handler> findAllByBundleAndHandlerTypeIn(Bundle bundle, Collection<HandlerType> handlerType);
	List<Handler> findAllByBundle(Bundle bundle);

	Collection<Handler> findAllByBundleAndHandledPayload_Name(Bundle bundle, String payloadName);
}