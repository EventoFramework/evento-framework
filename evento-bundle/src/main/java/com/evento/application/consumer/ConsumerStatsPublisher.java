package com.evento.application.consumer;

import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.messaging.consumer.ConsumerProcessor;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerStatsMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Pushes parallel-consumption counters to the server on a timer, so they can be exposed as
 * Micrometer meters on {@code /actuator/prometheus}.
 *
 * <p>Push rather than poll: the counters live in bundle-side objects (executor permits,
 * per-consumer trackers) that the server cannot see, and having it request them per scrape
 * would turn one scrape into a round-trip per consumer across the whole cluster.
 *
 * <p><b>Silent when unused.</b> A bundle with no {@code ConsumerExecutor} registered sends
 * nothing at all, so applications that have not opted into parallel consumption pay no wire
 * or scheduler cost beyond one idle timer thread.
 */
public final class ConsumerStatsPublisher implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(ConsumerStatsPublisher.class);

    private final EventoServer eventoServer;
    private final EngineSupervisor supervisor;
    private final ConsumerProcessor processor;
    private final Duration interval;
    private ScheduledExecutorService scheduler;

    public ConsumerStatsPublisher(EventoServer eventoServer,
                                  EngineSupervisor supervisor,
                                  ConsumerProcessor processor,
                                  Duration interval) {
        this.eventoServer = eventoServer;
        this.supervisor = supervisor;
        this.processor = processor;
        this.interval = interval;
    }

    /** No-op when the bundle has no executors — nothing to report. */
    public void start() {
        if (supervisor.getConsumerExecutors().isEmpty()) {
            logger.debug("No consumer executors registered — consumer stats push disabled");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = Thread.ofPlatform().unstarted(r);
            t.setName("evento-consumer-stats");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::publishQuietly,
                interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        logger.info("Publishing consumer executor stats every {}", interval);
    }

    private void publishQuietly() {
        try {
            eventoServer.send(snapshot());
        } catch (Exception e) {
            // Telemetry must never disturb consumption. A disconnected server is the
            // common case here and resolves itself on reconnect.
            logger.debug("Consumer stats push failed: {}", e.toString());
        }
    }

    /** Visible for tests: the message that would be sent right now. */
    public ConsumerStatsMessage snapshot() {
        var message = new ConsumerStatsMessage();

        var executors = new ArrayList<ConsumerStatsMessage.ExecutorStats>();
        for (var executor : supervisor.getConsumerExecutors()) {
            var stats = executor.stats();
            var dto = new ConsumerStatsMessage.ExecutorStats();
            dto.setName(stats.name());
            dto.setCapacity(stats.capacity());
            dto.setInFlight(stats.inFlight());
            dto.setAdmitted(stats.admitted());
            dto.setRejected(stats.rejected());
            dto.setCompleted(stats.completed());
            dto.setFailed(stats.failed());
            executors.add(dto);
        }
        message.setExecutors(executors);

        var consumers = new ArrayList<ConsumerStatsMessage.ConsumerStats>();
        for (var engine : supervisor.getProjectorEngines()) {
            consumers.add(consumerStats(engine.getConsumerId(), engine.getProjectorName()));
        }
        for (var engine : supervisor.getObserverEngines()) {
            consumers.add(consumerStats(engine.getConsumerId(), engine.getObserverName()));
        }
        message.setConsumers(consumers);

        return message;
    }

    private ConsumerStatsMessage.ConsumerStats consumerStats(String consumerId, String componentName) {
        var dto = new ConsumerStatsMessage.ConsumerStats();
        dto.setConsumerId(consumerId);
        dto.setComponentName(componentName);
        dto.setInFlight(processor.inFlightCount(consumerId));
        dto.setSubmitTimeouts(processor.asyncSubmitTimeouts(consumerId));
        dto.setTransientFailures(processor.asyncTransientFailures(consumerId));
        return dto;
    }

    @Override
    public void close() {
        if (scheduler != null) scheduler.shutdownNow();
    }
}
