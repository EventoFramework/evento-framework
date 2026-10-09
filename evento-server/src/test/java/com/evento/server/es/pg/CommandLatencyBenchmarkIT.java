package com.evento.server.es.pg;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.client.BundleClient;
import com.evento.application.client.EventoServerAdapter;
import com.evento.application.client.metrics.BundleLatencies;
import com.evento.application.client.metrics.LatencyHistogram;
import com.evento.common.messaging.gateway.CommandGatewayImpl;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.payload.DomainCommand;
import com.evento.common.modeling.state.SerializedAggregateState;
import com.evento.common.utils.PgDistributedLock;
import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.correlation.ForwardingDedupCache;
import com.evento.server.bus.event.BusEventBus;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
import com.evento.server.bus.security.TokenValidator;
import com.evento.server.config.DistributedLockConfiguration;
import com.evento.server.config.JsonConverter;
import com.evento.server.es.CommandBrokerHandler;
import com.evento.server.es.CommandPhaseMetrics;
import com.evento.server.es.CommandPhaseMetrics.Phase;
import com.evento.server.es.EventStore;
import com.evento.server.es.EventStoreMode;
import com.evento.server.es.pg.bench.BenchAddItem;
import com.evento.server.es.pg.bench.BenchCreate;
import com.evento.server.es.pg.bench.BenchCreated;
import com.evento.server.es.pg.bench.BenchItemAdded;
import com.evento.server.es.pg.bench.BenchSnapAddItem;
import com.evento.server.es.snapshot.Snapshot;
import com.evento.server.es.snapshot.SnapshotRepository;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.codec.JacksonCborCodec;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Where the round trip of an aggregate command goes, measured phase by phase on one machine (no
 * WAN): a real broker ({@link BusLifecycle} + {@link CommandBrokerHandler}, the production lock
 * on its own pool, the production {@link EventStore} in CPES on a real PostgreSQL with
 * {@code fsync} on), a real aggregate bundle ({@link EventoBundle} with the
 * {@code bench} aggregates) and a caller {@link BundleClient}, all over real TCP on loopback.
 *
 * <p>Per scenario (create; command on an aggregate with ~10 / ~100 events; ~100 events with a
 * snapshot every 20) × (unkeyed, keyed) × (1 or 8 in flight): {@value #DEFAULT_WARMUP} warm-up
 * commands, then {@value #DEFAULT_COMMANDS} measured ones. Reports p50/p95/p99 of every broker
 * phase ({@link CommandPhaseMetrics} samples), of the caller's round trip, of the aggregate
 * handler in the bundle, and the remainder (caller ↔ broker network and the caller's own
 * encode/decode), plus the statements each phase sends to the database. The baseline unit is
 * the measured {@code SELECT 1} round trip on the same pool: Docker Desktop runs PostgreSQL in a
 * VM, so a "local" round trip is not free.
 *
 * <p>Not a CI test: {@code EVENTO_RUN_BENCH=true} (Docker required). Knobs: {@code BENCH_COMMANDS},
 * {@code BENCH_WARMUP}. The report is printed and written to
 * {@code build/reports/command-latency-bench.md}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 60, unit = TimeUnit.MINUTES)
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_BENCH", matches = "true",
        disabledReason = "Benchmark: set EVENTO_RUN_BENCH=true (needs Docker).")
class CommandLatencyBenchmarkIT {

    static final int DEFAULT_COMMANDS = 2000;
    static final int DEFAULT_WARMUP = 500;
    private static final int LOCK_POOL_SIZE = 10;

    private final int commands = envInt("BENCH_COMMANDS", DEFAULT_COMMANDS);
    private final int warmup = envInt("BENCH_WARMUP", DEFAULT_WARMUP);

    private final List<AutoCloseable> closeables = new ArrayList<>();
    private final List<CommandPhaseMetrics.Sample> samples = new CopyOnWriteArrayList<>();
    private final StringBuilder report = new StringBuilder();
    private PostgreSQLContainer postgres;
    private HikariDataSource ds;
    private JdbcTemplate jdbc;
    private RoundTripCountingDataSource storeTrips;
    private RoundTripCountingDataSource lockTrips;
    private EventStore eventStore;
    private BusLifecycle lifecycle;
    private ExecutorService serverExecutor;
    private EventoBundle aggregateBundle;
    private BundleClient callerClient;
    private CommandGatewayImpl gateway;

    @BeforeAll
    void setup() throws Exception {
        // Production durability: fsync and synchronous_commit stay on (the other ITs turn fsync off).
        postgres = new PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("evento").withUsername("evento").withPassword("evento")
                .withCommand("postgres", "-c", "max_connections=200");
        postgres.start();
        PostgresItSupport.createDatabase(postgres, "bench");
        ds = PostgresItSupport.dataSource(postgres, PostgresItSupport.jdbcUrl(postgres, "bench"), 20);
        PostgresItSupport.initializer(ds, true).initializeDatabase();
        jdbc = new JdbcTemplate(ds);

        serverExecutor = Executors.newCachedThreadPool();
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var config = new NettyTransportConfig(
                Duration.ofSeconds(15), Duration.ofSeconds(45), Duration.ofSeconds(5),
                HandshakeProtocol.MAX_FRAME_LENGTH, 64 * 1024, 32 * 1024,
                new ExponentialBackoffWithJitter(), new JacksonCborCodec(), serverExecutor);
        lifecycle = new BusLifecycle(new NettyServerTransport(config),
                connections, cluster, new CorrelationStore(Duration.ofMillis(100)),
                new ForwardingTable(), eventBus, "bench-server",
                Set.of(HandshakeProtocol.CAPABILITY_PING_PONG), new JacksonCborPayloadCodec(),
                TokenValidator.acceptAll(), new ForwardingDedupCache());
        int port = lifecycle.start(0);

        storeTrips = new RoundTripCountingDataSource(ds);
        var storeJdbc = new JdbcTemplate(storeTrips.dataSource());
        // Production cache settings (evento.es.aggregate.*.cache: 1024 entries, 150 s).
        eventStore = new EventStore(null, jdbcSnapshots(storeJdbc), storeJdbc, storeTrips.dataSource(),
                1024, 1024, 150_000, 150_000, 69, EventStoreMode.CPES);
        var lockConfig = new DistributedLockConfiguration();
        var lockPool = lockConfig.eventoLockConnectionPool(ds, LOCK_POOL_SIZE, 1000);
        closeables.add(lockPool);
        lockTrips = new RoundTripCountingDataSource(lockPool.dataSource());
        var lock = new PgDistributedLock(lockTrips.dataSource(), Duration.ofSeconds(20), LOCK_POOL_SIZE);
        var broker = new CommandBrokerHandler(lifecycle, cluster, eventStore, lock);
        broker.bindTo(new SimpleMeterRegistry());
        broker.phaseMetrics().addListener(samples::add);

        aggregateBundle = EventoBundle.Builder.builder()
                .setBasePackage(BenchCreate.class.getPackage())
                .setBundleId("bench-aggregate")
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress("127.0.0.1", port)))
                .start();
        await().atMost(Duration.ofSeconds(30)).until(() -> lifecycle.isBundleAvailable("bench-aggregate")
                && !cluster.availableNodesFor("BenchSnapAddItem").isEmpty());

        callerClient = BundleClient.builder("bench-caller", "caller-" + UUID.randomUUID())
                .host("127.0.0.1").port(port).bundleVersion("1")
                .handlerPayloadTypes(List.of())
                .build();
        closeables.add(callerClient::close);
        callerClient.start().get(10, TimeUnit.SECONDS);
        gateway = new CommandGatewayImpl(new EventoServerAdapter(callerClient, "bench-caller", "caller-1", 1L));
    }

    @AfterAll
    void teardown() {
        if (aggregateBundle != null) {
            try { aggregateBundle.getEngineSupervisor().stop(Duration.ofSeconds(2)); } catch (Exception ignored) { }
            if (aggregateBundle.getEventoServer() instanceof EventoServerAdapter a) a.client().close();
        }
        for (var c : closeables.reversed()) {
            try { c.close(); } catch (Exception ignored) { /* best effort */ }
        }
        if (lifecycle != null) lifecycle.stop(Duration.ofMillis(500));
        if (serverExecutor != null) serverExecutor.shutdownNow();
        if (ds != null) ds.close();
        if (postgres != null) postgres.stop();
    }

    @Test
    void measure() throws Exception {
        line("# Aggregate command latency, local benchmark");
        line("");
        line("Generated by `CommandLatencyBenchmarkIT` on " + Instant.now() + ", JDK " + Runtime.version()
                + ", " + Runtime.getRuntime().availableProcessors() + " CPUs. PostgreSQL 16 in Docker"
                + " (fsync on), CPES, caches as in production. Warm-up " + warmup + ", measured " + commands
                + " per run. All times in ms.");
        line("");
        jdbcBaseline();

        // Warm-up: JIT, pools, caches, both aggregates.
        var warm = seedAggregates("warm", warmup / 10, 5, false);
        runCommands(warmup, 1, i -> new BenchAddItem(warm.get(i % warm.size()), "sku", 1), false);
        var warmSnap = seedAggregates("warm-snap", 5, 25, true);
        runCommands(Math.max(50, warmup / 5), 1,
                i -> new BenchSnapAddItem(warmSnap.get(i % warmSnap.size()), "sku", 1), false);

        var summary = new ArrayList<String>();
        for (int concurrency : new int[]{1, 8}) {
            for (boolean keyed : new boolean[]{false, true}) {
                String prefix = (keyed ? "k" : "u") + concurrency;
                summary.add(run("create", concurrency, keyed,
                        i -> new BenchCreate(prefix + "-new-" + UUID.randomUUID(), "EUROPA")));
                var ten = seedAggregates(prefix + "-e10", multipleOf8(commands / 10), 9, false);
                summary.add(run("existing ~10 events", concurrency, keyed,
                        i -> new BenchAddItem(ten.get(i % ten.size()), "sku", 1), ten.size()));
                var hundred = seedAggregates(prefix + "-e100", multipleOf8(commands / 40), 99, false);
                summary.add(run("existing ~100 events", concurrency, keyed,
                        i -> new BenchAddItem(hundred.get(i % hundred.size()), "sku", 1), hundred.size()));
                var snap = seedAggregates(prefix + "-s100", multipleOf8(commands / 40), 99, true);
                summary.add(run("existing ~100 events, snapshot/20", concurrency, keyed,
                        i -> new BenchSnapAddItem(snap.get(i % snap.size()), "sku", 1), snap.size()));
            }
        }
        line("");
        line("## Summary (p50 ms)");
        line("");
        line("| run | client RTT | server total | lock | story | forward | append | unlock | remainder |");
        line("|---|---:|---:|---:|---:|---:|---:|---:|---:|");
        summary.forEach(this::line);

        var out = Path.of("build", "reports", "command-latency-bench.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString());
        System.out.println(report);
        System.out.println("event=bench_report_written path=" + out.toAbsolutePath());
    }

    // ------------------------------------------------------------------------------------ runs

    private String run(String scenario, int concurrency, boolean keyed, IntFunction<DomainCommand> command)
            throws Exception {
        return run(scenario, concurrency, keyed, command, 0);
    }

    /**
     * One measured run. With {@code aggregates > 0}, command {@code i} goes to aggregate
     * {@code i % aggregates} and thread {@code t} only sends commands with {@code i % concurrency == t},
     * so no two in-flight commands share an aggregate (no lock contention: the aggregate count is a
     * multiple of 8).
     */
    private String run(String scenario, int concurrency, boolean keyed, IntFunction<DomainCommand> command,
                       int aggregates) throws Exception {
        samples.clear();
        callerClient.latencies().reset();
        var bundleLatencies = aggregateBundle.getLatencies();
        bundleLatencies.reset();
        storeTrips.reset();
        lockTrips.reset();
        long started = System.nanoTime();
        long[] rtt = runCommands(commands, concurrency, command, keyed);
        double seconds = (System.nanoTime() - started) / 1e9;
        await().atMost(Duration.ofSeconds(10)).until(() -> samples.size() >= commands);
        var snapshot = List.copyOf(samples);
        double storeStatements = storeTrips.count() / (double) commands;
        double lockStatements = lockTrips.count() / (double) commands;

        String title = scenario + ", " + (keyed ? "keyed" : "unkeyed") + ", " + concurrency + " in flight";
        line("");
        line("## " + title);
        line("");
        line(String.format(Locale.ROOT, "%d commands in %.1f s (%.0f/s)%s; DB statements per command: "
                        + "event store %.2f, lock pool %.2f.", commands, seconds, commands / seconds,
                aggregates > 0 ? ", over " + aggregates + " aggregates" : "", storeStatements, lockStatements));
        line("");
        line("| phase | n | p50 | p95 | p99 | mean |");
        line("|---|---:|---:|---:|---:|---:|");
        var p50 = new LinkedHashMap<String, Double>();
        for (var phase : Phase.values()) {
            long[] values = snapshot.stream().mapToLong(s -> s.nanos(phase)).filter(v -> v >= 0).toArray();
            if (values.length == 0) continue;
            p50.put(phase.tag(), row("server " + phase.tag(), values));
        }
        row("server other (in-memory steps)", snapshot.stream().mapToLong(CommandPhaseMetrics.Sample::otherNanos).toArray());
        double client = row("**client RTT** (gateway send → event)", rtt);
        histogramRow("caller BundleClient RTT (frame → frame)",
                callerClient.latencies().requestRtt().snapshot().values().stream()
                        .max(java.util.Comparator.comparingLong(LatencyHistogram.Snapshot::count)).orElse(null));
        var handlers = bundleLatencies.handlers().snapshot();
        histogramRow("bundle aggregate handler", handlers.get(BundleLatencies.AGGREGATE));
        histogramRow("bundle request decode", handlers.get(BundleLatencies.AGGREGATE + ".decode"));
        histogramRow("bundle answer encode", handlers.get(BundleLatencies.AGGREGATE + ".encode"));
        long[] remainder = remainder(rtt, snapshot, concurrency);
        double rem = row(concurrency == 1 ? "remainder (client RTT − server total, per command)"
                : "remainder (p-wise client RTT − server total)", remainder);

        assertThat(snapshot).hasSizeGreaterThanOrEqualTo(commands);
        return String.format(Locale.ROOT, "| %s | %.2f | %.2f | %s | %.2f | %.2f | %.2f | %s | %.2f |", title, client,
                p50.get("total"), fmt(p50.get("lock")), p50.get("story"), p50.get("forward"), p50.get("append"),
                fmt(p50.get("unlock")), rem);
    }

    /**
     * Client RTT − server total. With one command in flight the samples pair up in order (the
     * broker records a command when its reply is written, before the next one is sent); with
     * more, they cannot be paired, and the difference of the sorted arrays is reported.
     */
    private long[] remainder(long[] rtt, List<CommandPhaseMetrics.Sample> snapshot, int concurrency) {
        long[] total = snapshot.stream().mapToLong(s -> s.nanos(Phase.TOTAL)).toArray();
        int n = Math.min(rtt.length, total.length);
        long[] r = new long[n];
        if (concurrency == 1) {
            for (int i = 0; i < n; i++) r[i] = rtt[i] - total[i];
        } else {
            long[] a = rtt.clone();
            long[] b = total.clone();
            Arrays.sort(a);
            Arrays.sort(b);
            for (int i = 0; i < n; i++) r[i] = a[i] - b[i];
        }
        return r;
    }

    private long[] runCommands(int n, int concurrency, IntFunction<DomainCommand> command, boolean keyed)
            throws Exception {
        long[] rtt = new long[n];
        if (concurrency == 1) {
            for (int i = 0; i < n; i++) rtt[i] = send(command.apply(i), keyed);
            return rtt;
        }
        try (var pool = Executors.newFixedThreadPool(concurrency)) {
            var futures = new ArrayList<Future<?>>();
            for (int t = 0; t < concurrency; t++) {
                int thread = t;
                futures.add(pool.submit(() -> {
                    for (int i = thread; i < n; i += concurrency) rtt[i] = send(command.apply(i), keyed);
                    return null;
                }));
            }
            for (var f : futures) f.get(30, TimeUnit.MINUTES);
        }
        return rtt;
    }

    private long send(DomainCommand command, boolean keyed) throws Exception {
        long start = System.nanoTime();
        if (keyed) {
            gateway.sendIdempotent(command, "bench-" + UUID.randomUUID(), null, 30, TimeUnit.SECONDS)
                    .get(30, TimeUnit.SECONDS);
        } else {
            gateway.send(command, null, null, 30, TimeUnit.SECONDS).get(30, TimeUnit.SECONDS);
        }
        return System.nanoTime() - start;
    }

    /**
     * {@code count} aggregates with {@code events} events each: written straight into the event
     * store (fast), then one unmeasured command per aggregate so the broker's caches hold their
     * stories (and the snapshot aggregate has its first snapshot) as on a warm server. Each
     * aggregate ends with {@code events + 1} events.
     */
    private List<String> seedAggregates(String prefix, int count, int events, boolean snapshots) throws Exception {
        var ids = new ArrayList<String>();
        for (int a = 0; a < count; a++) {
            var id = prefix + "-" + UUID.randomUUID();
            ids.add(id);
            eventStore.publishEvent(new DomainEventMessage(new BenchCreated(id, "EUROPA")), id);
            for (int e = 1; e < events; e++) {
                eventStore.publishEvent(new DomainEventMessage(new BenchItemAdded(id, "seed", 1)), id);
            }
        }
        runCommands(ids.size(), 8, i -> snapshots
                ? new BenchSnapAddItem(ids.get(i), "touch", 1)
                : new BenchAddItem(ids.get(i), "touch", 1), false);
        return ids;
    }

    private void jdbcBaseline() {
        int n = Math.max(500, commands);
        for (int i = 0; i < 200; i++) jdbc.queryForObject("select 1", Integer.class);
        long[] pooled = new long[n];
        for (int i = 0; i < n; i++) {
            long t = System.nanoTime();
            jdbc.queryForObject("select 1", Integer.class);
            pooled[i] = System.nanoTime() - t;
        }
        long[] held = new long[n];
        jdbc.execute((java.sql.Connection c) -> {
            try (var st = c.prepareStatement("select 1")) {
                for (int i = 0; i < n; i++) {
                    long t = System.nanoTime();
                    try (var rs = st.executeQuery()) {
                        rs.next();
                    }
                    held[i] = System.nanoTime() - t;
                }
            }
            return null;
        });
        long[] commit = new long[n / 4];
        for (int i = 0; i < commit.length; i++) {
            long t = System.nanoTime();
            jdbc.update("insert into es__snapshot (aggregate_id, aggregate_state, event_sequence_number, updated_at) "
                    + "values (?, null, 0, now())", "baseline-" + UUID.randomUUID());
            commit[i] = System.nanoTime() - t;
        }
        line("## Baseline: one database round trip");
        line("");
        line("| measure | n | p50 | p95 | p99 | mean |");
        line("|---|---:|---:|---:|---:|---:|");
        row("`SELECT 1`, connection held", held);
        row("`SELECT 1` via JdbcTemplate (pool borrow + return)", pooled);
        row("single-row INSERT, autocommit (fsync)", commit);
    }

    // ------------------------------------------------------------------------------------ report

    private double row(String label, long[] nanos) {
        if (nanos.length == 0) return Double.NaN;
        long[] v = nanos.clone();
        Arrays.sort(v);
        double mean = Arrays.stream(v).average().orElse(0) / 1e6;
        double p50 = pct(v, 0.50);
        line(String.format(Locale.ROOT, "| %s | %d | %.3f | %.3f | %.3f | %.3f |", label, v.length, p50,
                pct(v, 0.95), pct(v, 0.99), mean));
        return p50;
    }

    private void histogramRow(String label, LatencyHistogram.Snapshot s) {
        if (s == null || s.count() == 0) return;
        line(String.format(Locale.ROOT, "| %s | %d | %.3f | %.3f | %.3f | %.3f |", label, s.count(),
                s.p50Nanos() / 1e6, s.p95Nanos() / 1e6, s.p99Nanos() / 1e6, s.meanNanos() / 1e6));
    }

    private static double pct(long[] sorted, double q) {
        int idx = (int) Math.ceil(q * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))] / 1e6;
    }

    private static String fmt(Double v) {
        return v == null ? "-" : String.format(Locale.ROOT, "%.2f", v);
    }

    private void line(String s) {
        report.append(s).append('\n');
    }

    /** At least 8 and a multiple of 8, so the threads of an 8-way run never share an aggregate. */
    private static int multipleOf8(int n) {
        return Math.max(8, (n + 7) / 8 * 8);
    }

    private static int envInt(String name, int fallback) {
        var v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : Integer.parseInt(v.trim());
    }

    /**
     * The snapshot repository over JDBC, for the two calls the event store makes. Production uses
     * Spring Data JPA, whose {@code save} of an entity with an assigned id is a merge: a SELECT and
     * then an INSERT or UPDATE, in a transaction. This upsert is one statement, so the bench's
     * {@code snapshot} phase is a lower bound of production's.
     */
    private static SnapshotRepository jdbcSnapshots(JdbcTemplate jdbc) {
        var converter = new JsonConverter();
        return (SnapshotRepository) Proxy.newProxyInstance(SnapshotRepository.class.getClassLoader(),
                new Class<?>[]{SnapshotRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> {
                        var rows = jdbc.query("select aggregate_id, aggregate_state, event_sequence_number, updated_at "
                                        + "from es__snapshot where aggregate_id = ?",
                                (rs, i) -> {
                                    var s = new Snapshot();
                                    s.setAggregateId(rs.getString(1));
                                    s.setAggregateState((SerializedAggregateState<?>) converter
                                            .convertToEntityAttribute(rs.getString(2)));
                                    s.setEventSequenceNumber(rs.getLong(3));
                                    Timestamp ts = rs.getTimestamp(4);
                                    s.setUpdatedAt(ts == null ? null : ts.toInstant());
                                    return s;
                                }, args[0]);
                        yield rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
                    }
                    case "save" -> {
                        var s = (Snapshot) args[0];
                        jdbc.update("insert into es__snapshot (aggregate_id, aggregate_state, event_sequence_number, "
                                        + "updated_at) values (?, ?, ?, ?) on conflict (aggregate_id) do update set "
                                        + "aggregate_state = excluded.aggregate_state, "
                                        + "event_sequence_number = excluded.event_sequence_number, "
                                        + "updated_at = excluded.updated_at",
                                s.getAggregateId(), converter.convertToDatabaseColumn((Serializable) s.getAggregateState()),
                                s.getEventSequenceNumber(), Timestamp.from(s.getUpdatedAt()));
                        yield s;
                    }
                    case "toString" -> "JdbcSnapshotRepository";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
