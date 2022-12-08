package org.evento.server.domain.repository;

import org.evento.common.modeling.bundle.types.ComponentType;
import org.evento.server.domain.model.Bundle;
import org.evento.server.domain.model.Handler;
import org.evento.common.modeling.bundle.types.HandlerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface HandlerRepository extends JpaRepository<Handler, String> {

	public void deleteAllByBundle_Id(String bunleId);

	public Handler findByHandledPayload_Name(String name);

	Collection<Handler> findAllByHandledPayload_Name(String payloadName);
	List<Handler> findAllByHandlerTypeOrderByUuid(HandlerType handlerType);

	@Query("select distinct h.handledPayload.name from Handler h where h.componentName = ?1")
	List<String> findAllHandledPayloadsNameByComponentName(String componentName);

	boolean existsByBundleAndHandledPayload_NameAndHandlerType(Bundle bundle, String payloadName, HandlerType handlerType);
	List<Handler> findAllByBundleAndHandlerType(Bundle bundle, HandlerType handlerType);
	List<Handler> findAllByBundleAndHandlerTypeIn(Bundle bundle, Collection<HandlerType> handlerType);
	List<Handler> findAllByBundle(Bundle bundle);
	List<Handler> findAllByBundle_Id(String id);

	Collection<Handler> findAllByBundleAndHandledPayload_Name(Bundle bundle, String payloadName);

	@Query("select count(h) > 0 from Handler h where h.bundle.id = ?1 and h.componentType = ?2 and h.componentName = ?3 and h.handlerType = ?4 and h.handledPayload.name = ?5" )
	boolean exists(String bundleId, ComponentType componentType, String componentName, HandlerType handlerType, String handledPayload);

}