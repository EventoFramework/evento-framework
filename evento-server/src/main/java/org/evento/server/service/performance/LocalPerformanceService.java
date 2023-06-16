package org.evento.server.service.performance;

import org.evento.common.performance.PerformanceInvocationsMessage;
import org.evento.common.performance.PerformanceService;
import org.evento.common.performance.PerformanceServiceTimeMessage;

public class LocalPerformanceService extends PerformanceService {
    public LocalPerformanceService(PerformanceStoreService performanceStoreService) {

    }

    @Override
    public void sendServiceTimeMetricMessage(PerformanceServiceTimeMessage message) throws Exception {

    }

    @Override
    public void sendInvocationMetricMessage(PerformanceInvocationsMessage message) throws Exception {

    }
}
