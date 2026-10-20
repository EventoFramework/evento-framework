package com.evento.server.es;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** O2: the consumer lag the server derives from the fetches it answers. */
class ConsumerLagMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AtomicLong now = new AtomicLong(Instant.parse("2026-10-20T12:00:00Z").toEpochMilli());
    private final AtomicLong head = new AtomicLong(1_000);
    private final ConsumerLagMetrics lag = new ConsumerLagMetrics(registry, head::get, now::get, true, 900_000);

    /** created_at holds UTC wall time; the metric must not depend on the JVM's zone. */
    private static Timestamp utc(String instant) {
        return Timestamp.valueOf(Instant.parse(instant).atOffset(ZoneOffset.UTC).toLocalDateTime());
    }

    private double gauge(String name, String context) {
        return registry.get(name).tag("bundle", "orders").tag("component", "OrderProjector")
                .tag("context", context).gauge().value();
    }

    @Test
    void lagIsTheAgeOfTheOldestUnprocessedEventAndKeepsGrowingWhileTheConsumerIsStuck() {
        lag.fetched("orders", "OrderProjector", "EU", 400, utc("2026-10-20T11:59:30Z"));
        assertThat(gauge("evento.consumer.lag.seconds", "EU")).isEqualTo(30.0);

        now.addAndGet(60_000); // no fetch: the handlers are stuck on that batch
        assertThat(gauge("evento.consumer.lag.seconds", "EU")).isEqualTo(90.0);
    }

    @Test
    void aFetchThatReturnsNothingMeansCaughtUp() {
        lag.fetched("orders", "OrderProjector", "EU", 400, utc("2026-10-20T11:59:30Z"));
        lag.fetched("orders", "OrderProjector", "EU", 1_000, null);
        assertThat(gauge("evento.consumer.lag.seconds", "EU")).isZero();
        assertThat(gauge("evento.consumer.position", "EU")).isEqualTo(1_000.0);
        assertThat(gauge("evento.consumer.lag.events", "EU")).isZero();
    }

    @Test
    void aConsumerThatStoppedAfterCatchingUpShowsInTheFetchAgeAndTheEventsBehind() {
        lag.fetched("orders", "OrderProjector", "EU", 1_000, null);
        now.addAndGet(120_000);
        head.set(1_300);
        assertThat(gauge("evento.consumer.lag.seconds", "EU")).as("its last fetch found nothing").isZero();
        assertThat(gauge("evento.consumer.fetch.age.seconds", "EU")).isEqualTo(120.0);
        assertThat(gauge("evento.consumer.lag.events", "EU")).isEqualTo(300.0);
    }

    @Test
    void eventsBehindAreTheStoreHeadMinusThePosition() {
        lag.fetched("orders", "OrderProjector", "EU", 400, utc("2026-10-20T11:59:59Z"));
        assertThat(gauge("evento.consumer.lag.events", "EU")).isEqualTo(600.0);
        head.set(1_500);
        now.addAndGet(1_000); // the head is read again at most once a second
        assertThat(gauge("evento.consumer.lag.events", "EU")).isEqualTo(1_100.0);
    }

    @Test
    void eachContextOfAComponentIsItsOwnSeries() {
        lag.fetched("orders", "OrderProjector", "EU", 900, null);
        lag.fetched("orders", "OrderProjector", "US", 100, utc("2026-10-20T11:50:00Z"));
        assertThat(gauge("evento.consumer.lag.seconds", "EU")).isZero();
        assertThat(gauge("evento.consumer.lag.seconds", "US")).isEqualTo(600.0);
    }

    @Test
    void aSeriesNobodyFetchesStopsReportingAndIsRemoved() {
        lag.fetched("orders", "OrderProjector", "EU", 400, utc("2026-10-20T11:59:30Z"));
        now.addAndGet(900_000);
        assertThat(gauge("evento.consumer.lag.seconds", "EU")).as("moved to another server").isNaN();

        lag.fetched("orders", "Other", "EU", 1, null); // any fetch sweeps
        assertThat(registry.find("evento.consumer.lag.seconds").tag("component", "OrderProjector").gauge()).isNull();
        assertThat(registry.find("evento.consumer.position").tag("component", "OrderProjector").gauge()).isNull();
    }

    @Test
    void underApesThereIsNoEventCount() {
        var apes = new ConsumerLagMetrics(registry, head::get, now::get, false, 900_000);
        apes.fetched("billing", "Invoicer", "all", 5, null);
        assertThat(registry.find("evento.consumer.lag.events").tag("bundle", "billing").gauge()).isNull();
        assertThat(registry.find("evento.consumer.lag.seconds").tag("bundle", "billing").gauge()).isNotNull();
    }
}
