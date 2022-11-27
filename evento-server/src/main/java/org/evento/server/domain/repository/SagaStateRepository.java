package org.evento.server.domain.repository;

import org.evento.server.domain.model.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SagaStateRepository extends JpaRepository<SagaState, Long> {
	@Query(nativeQuery = true, value =
			"select * from core__saga_state where saga_name = :sagaName and JSON_EXTRACT(saga_state, concat('$[1].associations[1].', :associationProperty)) = :associationValue")
	Optional<SagaState> getLastStatus(String sagaName, String associationProperty, String associationValue);
}