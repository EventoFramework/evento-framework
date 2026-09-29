package com.evento.lab.cluster.config;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.messaging.consumer.CheckpointMode;
import com.evento.common.messaging.consumer.ConsumerExecutors;
import com.evento.common.messaging.consumer.ConsumerProcessor;
import com.evento.common.performance.PerformanceService;
import com.evento.common.serialization.ObjectMapperUtils;
import com.evento.consumer.state.store.jdbc.JdbcConsumerLock;
import com.evento.consumer.state.store.jdbc.JdbcConsumerStateStore;
import com.evento.consumer.state.store.jdbc.JdbcDeadEventQueue;
import com.evento.consumer.state.store.jdbc.JdbcDedupeStore;
import com.evento.consumer.state.store.jdbc.JdbcSagaStateStore;
import com.evento.consumer.state.store.jdbc.SqlDialect;
import com.evento.lab.cluster.store.AppDatabase;
import com.evento.lab.cluster.store.OrderViewStore;
import com.evento.lab.cluster.store.SideEffectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Starts this JVM's Evento bundles for its role and keeps them. Start-up runs
 * on a background thread: the JVM (and its actuator health, reported DOWN
 * until every bundle is up) is alive while the evento-server is still
 * booting, which the chaos scenarios rely on (S10 cold start, S3 restarts).
 */
public class LabBundles implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LabBundles.class);

    private final LabSettings settings;
    private final AppDatabase database;
    private final Map<String, EventoBundle> started = new ConcurrentHashMap<>();
    private final Map<String, OrderViewStore> views = new ConcurrentHashMap<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final List<BundleSpec> specs;
    private volatile Instant readyAt;

    public LabBundles(LabSettings settings, AppDatabase database) {
        this.settings = settings;
        this.database = database;
        this.specs = BundleSpec.forSettings(settings);
        for (var spec : specs) {
            if (spec.viewTable() != null) {
                views.put(spec.viewTable(), new OrderViewStore(database.dataSource(), spec.viewTable(),
                        settings.bundleInstanceId(spec.bundleId())));
            }
        }
    }

    public void startAsync() {
        Thread.ofPlatform().name("lab-bundle-starter").daemon(true).start(this::startAll);
    }

    private void startAll() {
        for (int attempt = 1; ; attempt++) {
            try {
                database.initialise();
                break;
            } catch (Exception e) {
                lastError.set("app db init failed: " + e);
                log.error("event=lab_app_db_init_failed attempt={}", attempt, e);
                if (attempt >= 40) {
                    // Give up and let the container restart policy retry.
                    System.exit(3);
                }
                sleep(Duration.ofSeconds(3));
            }
        }
        for (var spec : specs) {
            int attempt = 0;
            while (!started.containsKey(spec.bundleId())) {
                attempt++;
                waitForFirstAddress();
                try {
                    var bundle = build(spec);
                    started.put(spec.bundleId(), bundle);
                    log.info("event=lab_bundle_started bundle={} instance={} addresses={} attempt={}",
                            spec.bundleId(), settings.bundleInstanceId(spec.bundleId()),
                            settings.serverAddresses(), attempt);
                } catch (Exception e) {
                    lastError.set(spec.bundleId() + ": " + e);
                    log.error("event=lab_bundle_start_failed bundle={} attempt={}", spec.bundleId(), attempt, e);
                    if (attempt >= 3) {
                        // A failed start leaves the bundle's connection supervisor
                        // running; retrying in-process would create a second session
                        // for the same bundle. Let the container restart instead.
                        System.exit(4);
                    }
                    sleep(Duration.ofSeconds(5));
                }
            }
        }
        readyAt = Instant.now();
        lastError.set(null);
        log.info("event=lab_ready role={} bundles={}", settings.role().envValue(), started.keySet());
    }

    private EventoBundle build(BundleSpec spec) throws Exception {
        var addresses = settings.serverAddresses().stream()
                .map(a -> new ClusterNodeAddress(a.host(), a.port()))
                .toArray(ClusterNodeAddress[]::new);
        // All addresses are passed on purpose: at HEAD the bundle dials only the
        // first one (G2); once address rotation lands the rest become failover
        // targets without touching the lab.
        var busConfig = new EventoServerMessageBusConfiguration(addresses);
        if (settings.expectedServerInstanceId() != null) {
            busConfig = busConfig.withExpectedServerInstanceId(settings.expectedServerInstanceId());
        }
        var injectables = injectables(spec);
        var builder = EventoBundle.Builder.builder()
                .setBasePackage(packageOf(spec))
                .setBundleId(spec.bundleId())
                .setInstanceId(settings.bundleInstanceId(spec.bundleId()))
                .setBundleVersion(1)
                .setDescription("evento-lab-cluster " + spec.bundleId() + " (" + settings.cell() + ")")
                .setEventoServerMessageBusConfiguration(busConfig)
                .setInjector(injectables::get)
                .setConsumerEngineConfigBuilder(this::jdbcEngineConfig);
        if ("WATERMARK".equals(settings.checkpointMode())) {
            builder.setCheckpointMode(CheckpointMode.WATERMARK);
        }
        for (var e : spec.contexts().entrySet()) {
            builder.setComponentContexts(Class.forName(spec.basePackage() + "." + e.getKey()),
                    e.getValue().toArray(String[]::new));
        }
        return builder.start();
    }

    private Map<Class<?>, Object> injectables(BundleSpec spec) {
        var m = new HashMap<Class<?>, Object>();
        m.put(SideEffectStore.class, new SideEffectStore(database.dataSource(),
                settings.bundleInstanceId(spec.bundleId())));
        if (spec.viewTable() != null) {
            m.put(OrderViewStore.class, views.get(spec.viewTable()));
        }
        return m;
    }

    private static Package packageOf(BundleSpec spec) throws ClassNotFoundException {
        // Package.getPackage only knows packages whose classes were already
        // loaded; load one component class to define it.
        var name = spec.basePackage();
        var candidates = new ArrayList<String>(spec.contexts().keySet());
        candidates.addAll(List.of("OrderAggregate", "GlobalOrderProjector", "PaymentSaga"));
        for (var candidate : candidates) {
            try {
                return Class.forName(name + "." + candidate).getPackage();
            } catch (ClassNotFoundException ignored) {
                // try the next one
            }
        }
        throw new ClassNotFoundException("no component class in " + name);
    }

    private ConsumerEngineConfig jdbcEngineConfig(EventoServer eventoServer, PerformanceService ps) {
        var ds = database.dataSource();
        var mapper = ObjectMapperUtils.getPayloadObjectMapper();
        var stateStore = new JdbcConsumerStateStore(ds, SqlDialect.POSTGRES);
        var deadEventQueue = new JdbcDeadEventQueue(ds, SqlDialect.POSTGRES, mapper);
        var processor = ConsumerProcessor.builder()
                .eventoServer(eventoServer)
                .lock(new JdbcConsumerLock(ds, SqlDialect.POSTGRES))
                .stateStore(stateStore)
                .sagaStateStore(new JdbcSagaStateStore(ds, SqlDialect.POSTGRES, mapper))
                .deadEventQueue(deadEventQueue)
                .dedupeStore(new JdbcDedupeStore(ds, SqlDialect.POSTGRES))
                .performanceService(ps)
                .observerExecutor(ConsumerExecutors.virtual("observer", ConsumerEngineConfig.DEFAULT_OBSERVER_CONCURRENCY))
                .build();
        return new ConsumerEngineConfig(processor, stateStore, deadEventQueue);
    }

    /**
     * Block until the first address accepts a TCP connection (at HEAD the
     * bundle dials only that one, G2). After a minute any reachable address
     * is accepted, so a JVM restarted while its own server is down still
     * comes up once the framework can use the others.
     */
    private void waitForFirstAddress() {
        var first = settings.serverAddresses().getFirst();
        long waited = 0;
        while (true) {
            for (var a : settings.serverAddresses()) {
                if (a != first && waited < 60) continue;
                if (reachable(a)) {
                    if (a != first) {
                        log.warn("event=lab_first_address_down using_reachable={} first={}", a, first);
                    }
                    return;
                }
            }
            if (waited % 30 == 0) {
                log.info("event=lab_waiting_for_server addresses={} waitedSeconds={}", settings.serverAddresses(), waited);
            }
            lastError.set("waiting for " + settings.serverAddresses());
            sleep(Duration.ofSeconds(2));
            waited += 2;
        }
    }

    private static boolean reachable(LabSettings.Address a) {
        try (var s = new Socket()) {
            s.connect(new InetSocketAddress(a.host(), a.port()), 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isReady() {
        return readyAt != null;
    }

    public Instant readyAt() {
        return readyAt;
    }

    public String lastError() {
        return lastError.get();
    }

    public List<String> expectedBundleIds() {
        var out = new ArrayList<String>();
        for (var s : specs) out.add(s.bundleId());
        return out;
    }

    public Map<String, EventoBundle> started() {
        return Map.copyOf(started);
    }

    /** The bundle whose gateways the HTTP layer uses (the first one of the role). */
    public Optional<EventoBundle> primaryBundle() {
        return Optional.ofNullable(started.get(specs.getFirst().bundleId()));
    }

    public Optional<OrderViewStore> view(String table) {
        return Optional.ofNullable(views.get(table));
    }

    public LabSettings settings() {
        return settings;
    }

    /**
     * The bus client state of each started bundle ({@code READY}, {@code DEGRADED}, ...).
     * Read reflectively: the accessor is not a stable API and the framework is
     * being changed in parallel, so the lab must not fail to compile over it.
     */
    public Map<String, String> busStates() {
        var out = new HashMap<String, String>();
        for (var e : started.entrySet()) {
            String state;
            try {
                var server = e.getValue().getEventoServer();
                var client = server.getClass().getMethod("client").invoke(server);
                state = String.valueOf(client.getClass().getMethod("state").invoke(client));
            } catch (Exception ex) {
                state = "unknown";
            }
            out.put(e.getKey(), state);
        }
        return out;
    }

    @Override
    public void close() {
        for (var b : started.values()) {
            try {
                b.getEngineSupervisor().stop(Duration.ofSeconds(10));
            } catch (Exception e) {
                log.warn("event=lab_bundle_stop_failed bundle={}", b.getBundleId(), e);
            }
        }
    }
}
