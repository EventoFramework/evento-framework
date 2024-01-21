package org.evento.server.performance.model;

import org.evento.server.service.performance.PerformanceStoreService;

public interface HasTarget {
    void addTarget(ActionNode s, PerformanceStoreService performanceStoreService);

    java.util.Map<Node, Double> getTarget();
}
