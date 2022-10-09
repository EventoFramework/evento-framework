package org.eventrails.server.domain.repository;

import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.model.Ranch;
import org.eventrails.server.domain.model.types.ComponentType;
import org.eventrails.server.domain.model.types.HandlerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface HandlerRepository extends JpaRepository<Handler, Ranch> {

	public void deleteAllByRanch_Name(String ranchName);

	public Handler findByHandledPayload_Name(String name);

	Collection<Handler> findAllByHandledPayload_Name(String payloadName);
	List<Handler> findAllByHandlerTypeOrderByUuid(HandlerType handlerType);

	boolean existsByRanchAndHandledPayload_NameAndHandlerType(Ranch ranch, String payloadName, HandlerType handlerType);
	List<Handler> findAllByRanchAndHandlerType(Ranch ranch, HandlerType handlerType);
	List<Handler> findAllByRanchAndHandlerTypeIn(Ranch ranch, Collection<HandlerType> handlerType);
	List<Handler> findAllByRanch(Ranch ranch);

	Collection<Handler> findAllByRanchAndHandledPayload_Name(Ranch ranch, String payloadName);
}