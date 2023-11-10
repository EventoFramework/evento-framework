package org.evento.server.domain.repository.performance;

import org.evento.server.domain.model.performance.HandlerInvocationCountPerformance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandlerInvocationCountPerformanceRepository extends JpaRepository<HandlerInvocationCountPerformance, String> {
}