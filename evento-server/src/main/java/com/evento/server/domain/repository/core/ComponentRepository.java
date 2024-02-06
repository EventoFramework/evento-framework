package com.evento.server.domain.repository.core;

import com.evento.server.domain.model.core.Component;
import com.evento.server.domain.repository.core.projection.ComponentListProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

@SuppressWarnings("Annotator")
public interface ComponentRepository extends JpaRepository<Component, String> {

	@Query(value = "select c.component_name as componentName, " +
			"   c.component_type as componentType, " +
			"   c.description, " +
			"   string_agg(distinct p.domain, ',') as domains, " +
			"   c.bundle_id as bundleId, " +
			"   count(distinct h.uuid) as handledMessages, " +
			"   count(h.return_type_name) as producedMessages, " +
			"   count(distinct concat(i.handler_uuid, i.invocations_key)) as invocations " +
			"from core__component c " +
			" left join core__handler h on c.component_name = h.component_component_name and h.handler_type <> 'EventSourcingHandler' " +
			"left join core__handler__invocation i on h.uuid = i.handler_uuid " +
			"left join core__payload p on h.handled_payload_name = p.name " +
			"group by c.component_name " +
			"order by c.updated_at", nativeQuery = true)
	List<ComponentListProjection> findAllComponentProjection();

	Optional<Component> findComponentByComponentNameAndBundle_Id(String componentName, String bundleId);

	List<Component> findAllByBundle_Id(String bundleId);

	@Query("select c.componentType as type, count(c) as count from Component c group by c.componentType")
	List<ComponentTypeCount> countByType();
}
