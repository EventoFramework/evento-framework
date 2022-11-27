package org.evento.server.domain.repository;

import org.evento.server.domain.performance.HandlerPerformances;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandlerPerformancesRepository extends JpaRepository<HandlerPerformances, String> {
}