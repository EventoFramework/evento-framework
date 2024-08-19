package com.evento.server.domain.repository.core;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.server.domain.model.core.Bundle;
import com.evento.server.domain.model.core.Component;
import com.evento.server.domain.model.core.Handler;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface HandlerRepository extends JpaRepository<Handler, String> {

	void deleteAllByComponent_Bundle_Id(String bundle);

	Handler findByHandledPayload_Name(String name);

	Collection<Handler> findAllByHandledPayload_Name(String payloadName);

	List<Handler> findAllByHandlerTypeOrderByUuid(HandlerType handlerType);

	@Query("select distinct h.handledPayload.name from Handler h where h.component.componentName = ?1")
	List<String> findAllHandledPayloadsNameByComponentName(String componentName);

	boolean existsByComponent_BundleAndHandledPayload_NameAndHandlerType(Bundle bundle, String payloadName, HandlerType handlerType);

	List<Handler> findAllByComponent_BundleAndHandlerType(Bundle bundle, HandlerType handlerType);

	List<Handler> findAllByComponent_BundleAndHandlerTypeIn(Bundle bundle, Collection<HandlerType> handlerType);

	List<Handler> findAllByComponent_Bundle(Bundle bundle);

	List<Handler> findAllByComponent_Bundle_Id(String id);

	Collection<Handler> findAllByComponent_BundleAndHandledPayload_Name(Bundle bundle, String payloadName);

	@Query("select count(h) > 0 from Handler h where h.component.bundle.id = ?1 and h.component.componentType = ?2 and h.component.componentName = ?3 and h.handlerType = ?4 and h.handledPayload.name = ?5")
	boolean exists(String bundleId, ComponentType componentType, String componentName, HandlerType handlerType, String handledPayload);


	List<Handler> findAllByComponent(Component c);
	List<Handler> findAllByComponentComponentName(String componentName);
}