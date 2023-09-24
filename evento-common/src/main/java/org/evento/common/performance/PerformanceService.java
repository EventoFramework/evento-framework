package org.evento.common.performance;

import org.evento.common.modeling.messaging.message.application.Message;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class PerformanceService {

    public static final String EVENT_STORE = "event-store";
    public static final String EVENT_STORE_COMPONENT = "EventStore";
    public static final String GATEWAY_COMPONENT = "Gateway";
    public static final String SERVER = "server";
    protected final Executor executor = Executors.newSingleThreadExecutor();

    private final Random random = new Random();
    private double performanceRate = 1;

    public PerformanceService(double rate) {
        performanceRate = rate;
    }

    public final void sendServiceTimeMetric(String bundle, String component, Message<?> message, Instant startTime) {
        if (random.nextDouble(0.0, 1.0) > performanceRate) return;
        executor.execute(() -> {
            var st = new PerformanceServiceTimeMessage(bundle, component, message.getPayloadName()
                    , startTime.toEpochMilli(), Instant.now().toEpochMilli());
            try {
                sendServiceTimeMetricMessage(st);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    protected abstract void sendServiceTimeMetricMessage(PerformanceServiceTimeMessage message) throws Exception;


    public void setPerformanceRate(double performanceRate) {
        this.performanceRate = performanceRate;
    }


    public final void sendInvocationsMetric(String bundle, String component, Message<?> action,
                                            HashMap<String, AtomicInteger> invocationCounter) {
        if (random.nextDouble(0.0, 1.0) > performanceRate) return;
        executor.execute(() -> {
            try {

                var invocations = new HashMap<String, Integer>();
                for (Map.Entry<String, AtomicInteger> e : invocationCounter.entrySet()) {
                    invocations.put(e.getKey(), e.getValue().get());
                }
                var pi = new PerformanceInvocationsMessage(bundle, component, action.getPayloadName(), invocations);
                sendInvocationMetricMessage(pi);

            } catch (Exception ignored) {

            }
        });
    }

    protected abstract void sendInvocationMetricMessage(PerformanceInvocationsMessage message) throws Exception;

}
