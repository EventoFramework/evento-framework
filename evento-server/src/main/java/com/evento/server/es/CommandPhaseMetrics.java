package com.evento.server.es;

import com.evento.server.bus.lifecycle.BusLifecycle;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Where the time of an aggregate command goes on the broker, phase by phase, from the moment its
 * frame reached this server to the moment the reply was written back to the caller.
 *
 * <p>Each executed aggregate command (its event appended; replays, refusals and drops are not
 * sampled) records one {@code evento.server.command.phase} timer per phase it went through,
 * tagged {@code phase} and {@code keyed} (whether the broker enforced an idempotency key):
 *
 * <table>
 *   <caption>Phases</caption>
 *   <tr><td>{@code queue}</td><td>frame decoded on the I/O thread → handler started (dispatch queue, broker dedup)</td></tr>
 *   <tr><td>{@code codec}</td><td>broker-side CBOR: request decode, forward encode (story included), handler answer decode, reply body encode</td></tr>
 *   <tr><td>{@code lock}</td><td>{@code RESOURCE:} advisory lock: lock-pool permit + connection borrow + {@code pg_try_advisory_lock} (and any wait for a holder)</td></tr>
 *   <tr><td>{@code story}</td><td>snapshot (when not cached) + events query; a keyed command's key check rides on it</td></tr>
 *   <tr><td>{@code forward}</td><td>send to the aggregate bundle → its answer arrived: network both ways + bundle dispatch + handler</td></tr>
 *   <tr><td>{@code verify}</td><td>{@code verifyHeld}: is the advisory lock still ours ({@code pg_locks}, one round trip)</td></tr>
 *   <tr><td>{@code append}</td><td>event insert, committed (CPES: one statement under the {@code es-lock} transaction lock)</td></tr>
 *   <tr><td>{@code snapshot}</td><td>snapshot save, only when the bundle returned one</td></tr>
 *   <tr><td>{@code unlock}</td><td>{@code pg_advisory_unlock} + connection back to the lock pool</td></tr>
 *   <tr><td>{@code reply}</td><td>handler returned → reply written to the caller's socket (response encode + write)</td></tr>
 *   <tr><td>{@code total}</td><td>frame decoded → reply written</td></tr>
 * </table>
 *
 * <p>The phases are consecutive laps of one {@link System#nanoTime()} stopwatch, so they add up
 * to {@code total} except for the in-memory steps between them (deadline checks, building the
 * decorated message), reported as {@code other} in the log line. Timers publish a percentile
 * histogram (0.1 ms to 30 s, ~85 buckets): p50/p95/p99 per server or across servers with
 * {@code histogram_quantile} (client-side percentiles would be dropped by the Prometheus
 * registry next to a histogram, so none are computed). One
 * {@code DEBUG} line per command, {@code event=command_phases} on logger {@value #LOGGER}, carries
 * the same numbers for offline analysis; at INFO it costs nothing.
 */
public final class CommandPhaseMetrics {

    /** Timer name. */
    public static final String METRIC = "evento.server.command.phase";

    /**
     * Logger of the per-command DEBUG line. All lower case so that it can be switched on from an
     * environment variable ({@code LOGGING_LEVEL_COM_EVENTO_SERVER_ES_PHASES=DEBUG}: Spring Boot
     * lower-cases the logger names it binds from the environment).
     */
    public static final String LOGGER = "com.evento.server.es.phases";

    private static final Logger log = LoggerFactory.getLogger(LOGGER);

    /** The phases of an aggregate command, in the order they happen. */
    public enum Phase {
        QUEUE, CODEC, LOCK, STORY, FORWARD, VERIFY, APPEND, SNAPSHOT, UNLOCK, REPLY, TOTAL;

        /** The {@code phase} tag value. */
        public String tag() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final Phase[] PHASES = Phase.values();

    private volatile Timer[][] timers;
    private final List<Consumer<Sample>> listeners = new CopyOnWriteArrayList<>();

    /** Registers the timers (two per phase: keyed and unkeyed). */
    void bindTo(MeterRegistry registry) {
        var t = new Timer[2][PHASES.length];
        for (int k = 0; k < 2; k++) {
            for (var phase : PHASES) {
                t[k][phase.ordinal()] = Timer.builder(METRIC)
                        .description("Time an executed aggregate command spent in one broker phase, arrival to reply written")
                        .tag("phase", phase.tag())
                        .tag("keyed", String.valueOf(k == 1))
                        .publishPercentileHistogram()
                        .minimumExpectedValue(Duration.ofNanos(100_000))
                        .maximumExpectedValue(Duration.ofSeconds(30))
                        .register(registry);
            }
        }
        this.timers = t;
    }

    /**
     * Receives every recorded sample, on the thread that wrote the reply. For benchmarks and
     * tests that need raw samples rather than the timers' summaries; keep it cheap.
     */
    public void addListener(Consumer<Sample> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<Sample> listener) {
        listeners.remove(listener);
    }

    /** A stopwatch for one command, started at its arrival. */
    Clock start(String payloadType, String correlationId, boolean keyed, BusLifecycle.RequestTiming timing) {
        long now = System.nanoTime();
        long arrival = timing == null ? now : timing.arrivalNanos();
        long dispatch = timing == null ? now : timing.dispatchNanos();
        return new Clock(payloadType, correlationId, keyed, arrival, dispatch);
    }

    /** The reply of {@code clock}'s command was written at {@code writtenNanos}: record it. */
    void record(Clock clock, long writtenNanos) {
        clock.nanos[Phase.REPLY.ordinal()] = Math.max(0, writtenNanos - clock.last);
        clock.nanos[Phase.TOTAL.ordinal()] = Math.max(0, writtenNanos - clock.arrival);
        var sample = new Sample(clock.payloadType, clock.correlationId, clock.keyed, clock.nanos.clone());
        var t = timers;
        if (t != null) {
            var row = t[clock.keyed ? 1 : 0];
            for (var phase : PHASES) {
                long n = sample.nanos[phase.ordinal()];
                if (n >= 0) row[phase.ordinal()].record(n, TimeUnit.NANOSECONDS);
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("event=command_phases payloadType={} correlationId={} keyed={} {}",
                    sample.payloadType, sample.correlationId, sample.keyed, sample.describe());
        }
        for (var listener : listeners) {
            try {
                listener.accept(sample);
            } catch (RuntimeException e) {
                log.debug("event=command_phase_listener_failed error=\"{}\"", e.getMessage());
            }
        }
    }

    /**
     * One command's stopwatch: each {@link #lap} charges the time since the previous lap to a
     * phase (a phase can be charged several times, e.g. {@code codec}). Not thread-safe: one
     * command runs on one thread until its reply is handed to the transport.
     */
    static final class Clock {
        final String payloadType;
        final String correlationId;
        final boolean keyed;
        final long arrival;
        final long[] nanos = new long[PHASES.length];
        long last;
        /** Set once the event is appended: only executed commands are sampled. */
        boolean appended;

        private Clock(String payloadType, String correlationId, boolean keyed, long arrival, long dispatch) {
            this.payloadType = payloadType;
            this.correlationId = correlationId;
            this.keyed = keyed;
            this.arrival = arrival;
            Arrays.fill(nanos, -1L);
            nanos[Phase.QUEUE.ordinal()] = Math.max(0, dispatch - arrival);
            this.last = dispatch;
        }

        void lap(Phase phase) {
            long now = System.nanoTime();
            int i = phase.ordinal();
            nanos[i] = Math.max(0, nanos[i]) + (now - last);
            last = now;
        }

        /** Restarts the stopwatch without charging anything (in-memory steps worth no phase). */
        void skip() {
            last = System.nanoTime();
        }
    }

    /**
     * The phases of one executed command, in nanoseconds; {@code -1} for a phase it did not go
     * through (no lock, no snapshot).
     */
    public record Sample(String payloadType, String correlationId, boolean keyed, long[] nanos) {

        /** Nanoseconds spent in {@code phase}, {@code -1} when the command did not go through it. */
        public long nanos(Phase phase) {
            return nanos[phase.ordinal()];
        }

        /** {@code total} minus the sum of the other phases: in-memory steps between them. */
        public long otherNanos() {
            long sum = 0;
            for (var phase : PHASES) {
                if (phase != Phase.TOTAL && nanos[phase.ordinal()] > 0) sum += nanos[phase.ordinal()];
            }
            return nanos(Phase.TOTAL) - sum;
        }

        String describe() {
            var sb = new StringBuilder(160);
            for (var phase : PHASES) {
                long n = nanos[phase.ordinal()];
                sb.append(phase.tag()).append('=');
                if (n < 0) sb.append('-');
                else sb.append(String.format(Locale.ROOT, "%.3fms", n / 1e6));
                sb.append(' ');
            }
            sb.append("other=").append(String.format(Locale.ROOT, "%.3fms", otherNanos() / 1e6));
            return sb.toString();
        }
    }
}
