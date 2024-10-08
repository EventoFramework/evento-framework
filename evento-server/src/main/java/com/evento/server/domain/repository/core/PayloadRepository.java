package com.evento.server.domain.repository.core;

import com.evento.server.domain.model.core.Payload;
import com.evento.server.domain.repository.core.projection.PayloadListProjection;
import com.evento.server.domain.repository.core.projection.PayloadProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PayloadRepository extends JpaRepository<Payload, String> {

	@Query(value = "select p.name, p.type, p.path, p.line, p.description, p.domain, count(distinct h.uuid) as subscriptions, " +
			"       string_agg(distinct h.component_component_name, ',') as subscribers, " +
			"       count(distinct i.handler_uuid) as invocations, " +
			"       count(distinct h2.uuid) as returnedBy " +
			"from core__payload p " +
			" left join core__handler h on p.name = h.handled_payload_name " +
			"left join core__handler__invocation i on i.invocations_name = p.name " +
			"left join core__handler h2 on h2.return_type_name = p.name " +
			"group by p.name order by p.updated_at desc", nativeQuery = true)
    List<PayloadListProjection> findAllProjection();

	@Query(value = "select p.name, " +
			"       p.json_schema                                                              as jsonSchema, " +
			"       p.registered_in                                                            as registeredIn, " +
			"       p.type, " +
			"       p.updated_at                                                               as updatedAt, " +
			"       p.description, " +
			"       p.detail, " +
			"       p.domain, " +
			"       p.path, " +
			"       p.line, " +
			"       max(b.line_prefix) as linePrefix, " +
			"       p.is_valid_json_schema as validJsonSchema, " +
			"       string_agg(distinct case when hc.component_name is null then null else concat(hc.component_name, '$$$', hc.component_type, '$$$', hc.path, '$$$', h.line) end, ',')   as subscribers, " +
			"       string_agg(distinct case when hic.component_name is null then null else concat(hic.component_name, '$$$', hic.component_type, '$$$', hic.path, '$$$', hi.line) end, ',') as invokers, " +
			"       string_agg(distinct case when h2c.component_name is null then null else concat(h2c.component_name, '$$$', h2c.component_type, '$$$', h2c.path, '$$$', h2.line) end, ',') as returnedBy, " +
			"       string_agg(distinct case when h3c.component_name is null then null else  concat(h3c.component_name, '$$$', h3c.component_type, '$$$', h3c.path, '$$$', h3.line) end, ',') as usedBy " +
			" " +
			"from core__payload p " +
			"         left join core__handler h on p.name = h.handled_payload_name and " +
			"                                      ((p.type != 'DomainEvent') or (h.handler_type != 'EventSourcingHandler')) " +
			"         left join core__component hc on h.component_component_name = hc.component_name " +
			"         left join core__bundle b on p.registered_in = b.id " +
			"         left join core__handler__invocation i on i.invocations_name = p.name " +
			"         left join core__handler hi on hi.uuid = i.handler_uuid " +
			"         left join core__component hic on hi.component_component_name = hic.component_name " +
			"         left join core__handler h2 on h2.return_type_name = p.name " +
			"         left join core__component h2c on h2.component_component_name = h2c.component_name " +
			"         left join core__handler__invocation ri on ri.invocations_name = h2.handled_payload_name " +
			"         left join core__handler h3 on h3.uuid = ri.handler_uuid " +
			"         left join core__component h3c on h3.component_component_name = h3c.component_name " +
			"where p.name = ?1 " +
			"group by p.name " +
			"order by p.updated_at desc", nativeQuery = true)
    Optional<PayloadProjection> findByIdProjection(String name);

	@Query("select p.type as type, count(p) as count from Payload p group by p.type")
    List<PayloadTypeCount> countByType();

}