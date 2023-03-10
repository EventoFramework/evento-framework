package org.evento.server.domain.repository;

import org.evento.server.domain.model.Payload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PayloadRepository extends JpaRepository<Payload, String> {
	
	@Query(value = "select p.name, p.type, p.description, p.domain, count(distinct h.uuid) as subscriptions, " +
			"       group_concat(distinct h.component_name) as subscribers, " +
			"       count(distinct i.handler_uuid) as invocations, " +
			"       count(distinct h2.uuid) as returnedBy " +
			"from core__payload p " +
			" left join core__handler h on p.name = h.handled_payload_name " +
			"left join core__handler__invocation i on i.invocations_name = p.name " +
			"left join core__handler h2 on h2.return_type_name = p.name " +
			"group by p.name order by p.updated_at desc", nativeQuery = true)
	public List<PayloadListProjection> findAllProjection();

	@Query(value = "select p.*,  " +
			"       group_concat(distinct h.component_name) as subscribers, " +
			"       group_concat(distinct hi.component_name) as invokers, " +
			"       group_concat(distinct h2.component_name) as returnedBy " +
			"from core__payload p " +
			" left join core__handler h on p.name = h.handled_payload_name " +
			"left join core__handler__invocation i on i.invocations_name = p.name " +
			"left join core__handler hi on hi.uuid = i.handler_uuid " +
			"left join core__handler h2 on h2.return_type_name = p.name " +
			"where p.name = ?1 group by p.name order by p.updated_at desc", nativeQuery = true)
	public Optional<PayloadProjection> findByIdProjection(String name);
}