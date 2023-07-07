package org.evento.server.domain.repository;

import org.evento.server.performance.HandlerInvocationCountPerformance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandlerInvocationCountPerformanceRepository extends JpaRepository<HandlerInvocationCountPerformance, String> {
}