package com.evento.server.domain.repository.performance;

import com.evento.server.domain.model.performance.HandlerServiceTimePerformance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandlerServiceTimePerformanceRepository extends JpaRepository<HandlerServiceTimePerformance, String> {
}