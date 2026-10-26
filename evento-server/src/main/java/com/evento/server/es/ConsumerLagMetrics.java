package com.evento.server.es;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * How far behind the event store each consumer is (O2), measured where every consumer's reads
 * pass: the server's answer to its event fetches. Tags {@code bundle}, {@code component} (the
 * projector, saga or observer) and {@code context}, so the per-context engines of one component
 * are separate series.
 *
 * <ul>
 *   <li>{@code evento.consumer.lag.seconds}: age of the oldest event the consumer has not
 *       processed yet, the first one its last fetch returned; 0 when that fetch came back
 *       empty (caught up). Read at scrape time, so it keeps growing while a consumer is stuck
 *       in its handlers and does not fetch. <b>The one to alert on.</b></li>
 *   <li>{@code evento.consumer.lag.events}: the newest sequence number in the store minus the
 *       consumer's position. CPES only (APES ids are not counts); it counts the events of
 *       every context, so for a consumer of one context it is an upper bound.</li>
 *   <li>{@code evento.consumer.position}: the sequence number the consumer fetched after, its
 *       checkpoint.</li>
 *   <li>{@code evento.consumer.fetch.age.seconds}: time since the consumer last fetched. A consumer
 *       whose last fetch found nothing and that then stopped (crashed, lost its lock, disabled)
 *       keeps {@code lag.seconds} at 0; this, and {@code lag.events}, show it.</li>
 * </ul>
 *
 * <p>Each server knows the consumers that fetch through it. A series nobody has fetched for
 * {@code evento.consumer.lag.expiry} (default 15 minutes) is removed, so a consumer that moved to
 * another server or went away does not leave one behind; meanwhile the old server's series keeps
 * growing like a stuck consumer's would. Across servers, aggregate with {@code min by (bundle,
 * component, context)}: the server the consumer fetches through now has the true value.
 */
@Component
public class ConsumerLagMetrics {

    /** How often the newest sequence number is read again: one indexed query, not one per fetch. */
    private static final long HEAD_REFRESH_MS = 1_000;

    private final MeterRegistry registry;
    private final LongSupplier head;
    private final LongSupplier clock;
    private final boolean countsEvents;
    private final long expiryMs;
    private final Map<Key, Series> series = new ConcurrentHashMap<>();

    private volatile long headValue;
    private volatile long headReadAt = Long.MIN_VALUE;
    private volatile long lastSweep;

    @Autowired
    public ConsumerLagMetrics(MeterRegistry registry, EventStore eventStore,
                              @Value("${evento.es.mode:CPES}") EventStoreMode mode,
                              @Value("${evento.consumer.lag.expiry:PT15M}") java.time.Duration expiry) {
        this(registry, eventStore::getLastEventSequenceNumber, System::currentTimeMillis,
                mode == EventStoreMode.CPES, expiry.toMillis());
    }

    ConsumerLagMetrics(MeterRegistry registry, LongSupplier head, LongSupplier clock,
                       boolean countsEvents, long expiryMs) {
        this.registry = registry;
        this.head = head;
        this.clock = clock;
        this.countsEvents = countsEvents;
        this.expiryMs = expiryMs;
    }

    private record Key(String bundle, String component, String context) {
    }

    /** Values the meters read; held strongly, Micrometer keeps only weak references. */
    private static final class Series {
        volatile long position;
        /** Commit time (epoch ms) of the oldest unprocessed event; 0 when caught up. */
        volatile long oldestPendingAt;
        volatile long lastFetch;
        final List<Meter.Id> meters = new ArrayList<>();
    }

    /**
     * A fetch was answered.
     *
     * @param after          the sequence number the consumer fetched after
     * @param firstCreatedAt commit time of the first event returned, {@code null} when none
     */
    public void fetched(String bundle, String component, String context, long after, java.time.Instant firstCreatedAt) {
        if (component == null) return;
        long now = clock.getAsLong();
        var key = new Key(bundle == null ? "unknown" : bundle, component, context == null ? "all" : context);
        var s = series.computeIfAbsent(key, this::register);
        s.position = after;
        s.oldestPendingAt = firstCreatedAt == null ? 0 : firstCreatedAt.toEpochMilli();
        s.lastFetch = now;
        sweep(now);
    }

    private Series register(Key key) {
        var s = new Series();
        var tags = new String[]{"bundle", key.bundle(), "component", key.component(), "context", key.context()};
        s.meters.add(Gauge.builder("evento.consumer.lag.seconds", s, x -> lagSeconds(x))
                .description("Age of the oldest event the consumer has not processed yet; 0 when caught up")
                .baseUnit("seconds").tags(tags).register(registry).getId());
        s.meters.add(Gauge.builder("evento.consumer.fetch.age.seconds", s,
                        x -> Math.max(0, clock.getAsLong() - x.lastFetch) / 1000.0)
                .description("Time since the consumer last fetched through this server")
                .baseUnit("seconds").tags(tags).register(registry).getId());
        s.meters.add(Gauge.builder("evento.consumer.position", s, x -> x.position)
                .description("Sequence number the consumer last fetched after (its checkpoint)")
                .tags(tags).register(registry).getId());
        if (countsEvents) {
            s.meters.add(Gauge.builder("evento.consumer.lag.events", s, x -> Math.max(0, head() - x.position))
                    .description("Newest sequence number in the store minus the consumer's position (all contexts)")
                    .baseUnit("events").tags(tags).register(registry).getId());
        }
        return s;
    }

    private double lagSeconds(Series s) {
        long now = clock.getAsLong();
        // Not fetched through this server for the expiry: no value until the sweep removes it.
        if (now - s.lastFetch >= expiryMs) return Double.NaN;
        long oldest = s.oldestPendingAt;
        return oldest == 0 ? 0 : Math.max(0, now - oldest) / 1000.0;
    }

    private long head() {
        long now = clock.getAsLong();
        if (headReadAt == Long.MIN_VALUE || now - headReadAt >= HEAD_REFRESH_MS) {
            headValue = head.getAsLong();
            headReadAt = now;
        }
        return headValue;
    }

    /** Drops the series nobody fetched for {@code expiryMs}; at most once a minute. */
    private void sweep(long now) {
        if (now - lastSweep < 60_000) return;
        lastSweep = now;
        series.entrySet().removeIf(e -> {
            if (now - e.getValue().lastFetch < expiryMs) return false;
            e.getValue().meters.forEach(registry::remove);
            return true;
        });
    }
}
