package org.evento.server.domain.repository.core;

import org.evento.server.domain.model.core.Bundle;
import org.evento.server.domain.repository.core.projection.BundleListProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BundleRepository extends JpaRepository<Bundle, String> {

	@Query(value = "select b.id, b.version, b.autorun, b.bucket_type, b.description, count(distinct c.component_name) as components, string_agg(distinct p.domain, ',') as domains " +
			"from core__bundle b left join core__component c on b.id = c.bundle_id " +
			" left join core__handler h on h.component_component_name = c.component_name " +
			"left join core__payload p on h.handled_payload_name = p.name or p.registered_in = b.id " +
			"group by b.id " +
			"order by b.updated_at;", nativeQuery = true)
	List<BundleListProjection> findAllProjection();

	@Query("select count(b) from Bundle b where b.containsHandlers = true")
	Long countDeployable();
}