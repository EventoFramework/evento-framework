package org.eventrails.server.domain.repository;

import org.eventrails.server.domain.performance.HandlerPerformances;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandlerPerformancesRepository extends JpaRepository<HandlerPerformances, String> {
}