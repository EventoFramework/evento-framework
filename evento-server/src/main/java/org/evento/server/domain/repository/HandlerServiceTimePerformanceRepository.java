package org.evento.server.domain.repository;

import org.evento.server.domain.performance.HandlerServiceTimePerformance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandlerServiceTimePerformanceRepository extends JpaRepository<HandlerServiceTimePerformance, String> {
}