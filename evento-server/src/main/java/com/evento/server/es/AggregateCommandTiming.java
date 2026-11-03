package com.evento.server.es;

import java.time.Instant;

/**
 * When one stored aggregate command reached each stage, for the console's telemetry. The stages are
 * consecutive: {@code start} is its arrival, so up to {@code lockAcquired} is the wait before the run
 * that stored it (a lock, and any runs that lost the fence).
 */
public record AggregateCommandTiming(
        String bundleId,
        String component,
        String commandName,
        String sourceBundleId,
        String sourceInstanceId,
        String aggregateId,
        long eventSequenceNumber,
        Instant start,
        Instant lockAcquired,
        Instant retrieveDone,
        Instant computationDone,
        Instant published,
        boolean forceTelemetry
) {
}
