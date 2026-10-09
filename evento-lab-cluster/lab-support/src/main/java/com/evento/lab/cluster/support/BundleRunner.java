package com.evento.lab.cluster.support;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Starts the JVM's one Evento bundle from a {@link BundleDeployment} and a
 * {@link BundleDefinition}, and keeps it.
 *
 * <p>Start-up runs on a background thread: the JVM, and its actuator health
 * (DOWN until the bundle handshook), is alive while the evento-server is still
 * booting, which the chaos scenarios rely on (S10 cold start, S3 restarts).
 * A failed start exits the JVM (code 3: database, code 4: bundle) and the
 * container restart policy retries: a failed {@code EventoBundle.start} leaves
 * its connection supervisor running, so retrying in-process would open a
 * second session for the same instance.
 */
public class BundleRunner implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BundleRunner.class);

    private final BundleDeployment deployment;
    private final BundleDefinition definition;
    private final LabDatabases databases;
    private final FrameworkFeatures features = new FrameworkFeatures();
    private final AtomicReference<EventoBundle> bundle = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private volatile Instant readyAt;

    public BundleRunner(BundleDeployment deployment, BundleDefinition definition, LabDatabases databases) {
        this.deployment = deployment;
        this.definition = definition;
        this.databases = databases;
    }

    public void startAsync() {
        Thread.ofPlatform().name("lab-bundle-starter").daemon(true).start(this::start);
    }

    private void start() {
        log.info("event=lab_deployment bundle={} instance={} zone={} cell={} region={} contexts={} plan={} "
                        + "addresses={} appDb={} consumerStateDb={} targets={}",
                deployment.bundleId(), deployment.instanceId(), deployment.zone(), deployment.cell(),
                deployment.region(), deployment.contexts(), definition.consumerPlan(deployment),
                deployment.serverAddresses(), deployment.appDb(), deployment.consumerStateDb(),
                deployment.dataTargets().keySet());
        for (int attempt = 1; ; attempt++) {
            try {
                databases.initialise(definition.usesConsumers());
                break;
            } catch (Exception e) {
                lastError.set("database init failed: " + e);
                log.error("event=lab_db_init_failed attempt={}", attempt, e);
                if (attempt >= 40) {
                    System.exit(3);
                }
                sleep(Duration.ofSeconds(3));
            }
        }
        boolean failover = features.endpointFailover(deployment.serverAddresses().size());
        for (int attempt = 1; bundle.get() == null; attempt++) {
            waitForServer(failover);
            try {
                bundle.set(build());
                log.info("event=lab_bundle_started bundle={} instance={} addresses={} attempt={}",
                        deployment.bundleId(), deployment.instanceId(), deployment.serverAddresses(), attempt);
            } catch (Exception e) {
                lastError.set(e.toString());
                log.error("event=lab_bundle_start_failed bundle={} attempt={}", deployment.bundleId(), attempt, e);
                if (attempt >= 3) {
                    System.exit(4);
                }
                sleep(Duration.ofSeconds(5));
            }
        }
        readyAt = Instant.now();
        lastError.set(null);
        log.info("event=lab_ready bundle={} instance={}", deployment.bundleId(), deployment.instanceId());
    }

    private EventoBundle build() throws Exception {
        var addresses = deployment.serverAddresses().stream()
                .map(a -> new ClusterNodeAddress(a.host(), a.port()))
                .toArray(ClusterNodeAddress[]::new);
        // Every address is passed: the framework before G2 dials only the first,
        // after G2 the rest are failover targets, with no change here.
        var busConfig = new EventoServerMessageBusConfiguration(addresses);
        if (deployment.expectedServerId() != null) {
            busConfig = busConfig.withExpectedServerInstanceId(deployment.expectedServerId());
        }
        busConfig = features.expectedClusterName(busConfig, deployment.clusterName());
        var injectables = new HashMap<>(definition.injectables());
        var builder = EventoBundle.Builder.builder()
                .setBasePackage(definition.anchor().getPackage())
                .setBundleId(deployment.bundleId())
                .setInstanceId(deployment.instanceId())
                .setBundleVersion(1)
                .setDescription("evento-lab-cluster " + deployment.bundleId()
                        + (deployment.zone() == null ? "" : " (" + deployment.zone() + ")"))
                .setEventoServerMessageBusConfiguration(busConfig)
                .setInjector(injectables::get);
        if (definition.usesConsumers()) {
            var ds = databases.consumerState();
            builder.setConsumerEngineConfigBuilder((server, ps) -> jdbcEngineConfig(ds, server, ps));
        }
        if (deployment.checkpointMode().equals("WATERMARK")) {
            builder.setCheckpointMode(CheckpointMode.WATERMARK);
        } else if (deployment.checkpointMode().equals("ON_START")) {
            builder.setCheckpointMode(CheckpointMode.ON_START);
        }
        features.zone(builder, deployment.zone());
        features.heartbeatReadIdle(builder, deployment.heartbeatReadIdle());
        features.failback(builder, deployment.failbackWindow());
        definition.configure(builder, deployment, features);
        return builder.start();
    }

    static ConsumerEngineConfig jdbcEngineConfig(DataSource ds, EventoServer eventoServer, PerformanceService ps) {
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
     * Block until a server accepts TCP. Without endpoint failover (before G2)
     * the bundle dials only the first address, so that one is awaited; after a
     * minute any reachable address is accepted, so a JVM restarted while its
     * own server is down still comes up once the framework can use the others.
     * With failover any address will do from the start.
     */
    private void waitForServer(boolean failover) {
        var first = deployment.serverAddresses().getFirst();
        long waited = 0;
        while (true) {
            for (var a : deployment.serverAddresses()) {
                if (!failover && a != first && waited < 60) continue;
                if (reachable(a)) {
                    if (a != first) {
                        log.warn("event=lab_first_address_down using_reachable={} first={}", a, first);
                    }
                    return;
                }
            }
            if (waited % 30 == 0) {
                log.info("event=lab_waiting_for_server addresses={} waitedSeconds={}",
                        deployment.serverAddresses(), waited);
            }
            lastError.set("waiting for " + deployment.serverAddresses());
            sleep(Duration.ofSeconds(2));
            waited += 2;
        }
    }

    private static boolean reachable(BundleDeployment.Address a) {
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

    public Optional<EventoBundle> bundle() {
        return Optional.ofNullable(bundle.get());
    }

    public BundleDeployment deployment() {
        return deployment;
    }

    public BundleDefinition definition() {
        return definition;
    }

    public FrameworkFeatures features() {
        return features;
    }

    /**
     * The bus client state ({@code READY}, {@code DEGRADED}, ...). Read
     * reflectively: the accessor is not a stable API and the framework changes
     * in parallel, so the lab must not fail to compile over it.
     */
    public String busState() {
        var b = bundle.get();
        if (b == null) return "not started";
        try {
            var server = b.getEventoServer();
            var client = server.getClass().getMethod("client").invoke(server);
            return String.valueOf(client.getClass().getMethod("state").invoke(client));
        } catch (Exception ex) {
            return "unknown";
        }
    }

    public Map<String, Object> describe() {
        var out = new java.util.LinkedHashMap<String, Object>();
        out.put("bundleId", deployment.bundleId());
        out.put("instance", deployment.instanceId());
        out.put("zone", deployment.zone());
        out.put("cell", deployment.cell());
        out.put("region", deployment.region());
        out.put("contexts", deployment.contexts() == null ? null : deployment.contexts().contexts());
        out.put("consumerPlan", definition.consumerPlan(deployment));
        out.put("server", deployment.serverAddresses().getFirst().toString());
        out.put("addresses", deployment.serverAddresses().stream().map(Object::toString).toList());
        out.put("clusterName", deployment.clusterName());
        out.put("expectedServerInstanceId", deployment.expectedServerId());
        out.put("checkpointMode", deployment.checkpointMode());
        out.put("appDb", deployment.appDb() == null ? null : deployment.appDb().url());
        out.put("consumerStateDb", deployment.consumerStateDb() == null ? null : deployment.consumerStateDb().url());
        out.put("dataTargets", deployment.dataTargets().keySet());
        out.put("frameworkFeatures", features.outcomes());
        out.put("busState", busState());
        out.put("ready", isReady());
        return out;
    }

    @Override
    public void close() {
        var b = bundle.get();
        if (b != null) {
            try {
                b.getEngineSupervisor().stop(Duration.ofSeconds(10));
            } catch (Exception e) {
                log.warn("event=lab_bundle_stop_failed bundle={}", b.getBundleId(), e);
            }
        }
    }
}
