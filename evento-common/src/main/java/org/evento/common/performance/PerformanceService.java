package org.evento.common.performance;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.modeling.messaging.message.application.Message;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PerformanceService is an abstract class that provides functionality for sending performance metrics.
 */
public abstract class PerformanceService {

    private final Logger logger = LogManager.getLogger(PerformanceService.class);

    public static final String EVENT_STORE = "event-store";
    public static final String EVENT_STORE_COMPONENT = "EventStore";
    public static final String GATEWAY_COMPONENT = "Gateway";
    public static final String SERVER = "server";
    protected final Executor executor = Executors.newSingleThreadExecutor();

    private final Random random = new Random();
    private double performanceRate;

    /**
     * Constructs a new PerformanceService object with the given rate.
     *
     * @param rate The rate at which performance metrics are captured.
     */
    public PerformanceService(double rate) {
        performanceRate = rate;
    }

    /**
     * Sends a service time metric.
     * <p>
     * This method sends a service time metric with the given parameters. It calculates the service time using the provided start time and the current time. The service time metric
     * is wrapped in a PerformanceServiceTimeMessage object and sent asynchronously using the executor. If an exception occurs while sending the metric, an error message is logged
     *.
     *
     * @param bundle    The bundle of the metric.
     * @param component The component of the metric.
     * @param message   The message associated with the metric.
     * @param startTime The start time of the service.
     */
    public final void sendServiceTimeMetric(String bundle, String component, Message<?> message, Instant startTime) {
        if (random.nextDouble(0.0, 1.0) > performanceRate) return;
        var time = Instant.now().toEpochMilli();
        executor.execute(() -> {
            var st = new PerformanceServiceTimeMessage(bundle, component, message.getPayloadName()
                    , startTime.toEpochMilli(), time);
            try {
                sendServiceTimeMetricMessage(st);
            } catch (Exception e) {
               logger.error("Error during performance save", e);
            }
        });
    }

    /**
     * Sends a service time metric message.
     * <p>
     * This method sends a service time metric with the provided information. It calculates the service time using the provided start time
     * and the current time. The service time metric is wrapped in a PerformanceServiceTimeMessage object and sent asynchronously using
     * the executor. If an exception occurs while sending the metric, an error message is logged.
     *
     * @param message The PerformanceServiceTimeMessage object containing the service time metric information.
     * @throws Exception If an error occurs while sending the metric.
     */
    protected abstract void sendServiceTimeMetricMessage(PerformanceServiceTimeMessage message) throws Exception;


    /**
     * Sets the performance rate for capturing performance metrics.
     *
     * @param performanceRate The rate at which performance metrics are captured.
     */
    public void setPerformanceRate(double performanceRate) {
        this.performanceRate = performanceRate;
    }


    /**
     * Sends a performance invocations metric asynchronously.
     * <p>
     * This method sends a performance invocations metric with the specified bundle, component, action, and invocation counter.
     * The invocation counter is a HashMap that contains the number of invocations for each key.
     * The performance invocations metric is encapsulated in a PerformanceInvocationsMessage object.
     * The method executes the sending logic on a separate thread using an executor.
     * If an exception occurs while sending the metric, it is ignored.
     *
     * @param bundle             The bundle associated with the performance invocations metric.
     * @param component          The component associated with the performance invocations metric.
     * @param action             The message associated with the performance invocations metric.
     * @param invocationCounter  The invocation counter containing the number of invocations for each key.
     */
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

    /**
     * Sends a performance invocations metric message.
     * <p>
     * This method sends a performance invocations metric message with the provided information.
     * The performance invocations metric message is encapsulated in a PerformanceInvocationsMessage object.
     * The method executes the sending logic on a separate thread using an executor.
     * If an exception occurs while sending the metric message, it is thrown.
     *
     * @param message The PerformanceInvocationsMessage object containing the performance invocations metric information.
     * @throws Exception If an error occurs while sending the metric message.
     */
    protected abstract void sendInvocationMetricMessage(PerformanceInvocationsMessage message) throws Exception;

}
