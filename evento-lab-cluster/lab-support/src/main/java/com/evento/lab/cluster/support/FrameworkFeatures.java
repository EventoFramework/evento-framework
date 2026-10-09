package com.evento.lab.cluster.support;

import com.evento.application.EventoBundle;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.payload.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A reflective shim over framework features that exist only once the cluster
 * fixes land: {@code EventoBundle.Builder.setZone} (locality routing, G12),
 * {@code EventoServerMessageBusConfiguration.withExpectedClusterName} (G13),
 * {@code setHeartbeatReadIdle} / {@code setDefaultRequestTimeout} /
 * {@code setMaxInFlightRequests} (G22), address-list failover (G2) and
 * {@code CommandGateway.sendIdempotent} (idempotency keys, G5).
 *
 * <p>Why reflection: the same lab source must compile and run against the
 * framework before the fixes (images tagged {@code before}, built with
 * {@code FRAMEWORK_REF}) and after them. A feature that is absent is not an
 * error, it is part of the evidence: it is logged once as
 * {@code event=lab_framework_feature status=unavailable} and reported by
 * {@code /whoami}, so a scenario report can say which features its images had.
 */
public final class FrameworkFeatures {

    private static final Logger log = LoggerFactory.getLogger(FrameworkFeatures.class);

    /** What happened to one requested feature. */
    public record Outcome(String feature, boolean available, String value) {
    }

    private final List<Outcome> outcomes = new ArrayList<>();

    public synchronized List<Outcome> outcomes() {
        return List.copyOf(outcomes);
    }

    private synchronized void record(String feature, boolean available, Object value) {
        outcomes.removeIf(o -> o.feature().equals(feature));
        outcomes.add(new Outcome(feature, available, String.valueOf(value)));
        if (available) {
            log.info("event=lab_framework_feature feature={} status=applied value={}", feature, value);
        } else {
            log.warn("event=lab_framework_feature feature={} status=unavailable value={} "
                    + "(framework without this fix: running without it)", feature, value);
        }
    }

    /** Advertise the zone for locality-preferred routing ({@code Builder.setZone}). */
    public void zone(EventoBundle.Builder builder, String zone) {
        if (zone == null) return;
        record("zone", invoke(builder, "setZone", String.class, zone), zone);
    }

    public void heartbeatReadIdle(EventoBundle.Builder builder, Duration readIdle) {
        if (readIdle == null) return;
        record("heartbeatReadIdle", invoke(builder, "setHeartbeatReadIdle", Duration.class, readIdle), readIdle);
    }

    /**
     * Fail-back to the first address after a failover (N16). Recorded on every
     * start, so {@code /whoami} tells a scenario whether its images can fail
     * back at all; {@code window} (null = the framework's 30 s) is applied
     * through {@code Builder.setFailbackStabilityWindow} when present.
     */
    public void failback(EventoBundle.Builder builder, Duration window) {
        boolean available = window == null
                ? find(builder.getClass(), "setFailbackStabilityWindow", Duration.class) != null
                : invoke(builder, "setFailbackStabilityWindow", Duration.class, window);
        record("failback", available, window == null ? "default window" : window);
    }

    public void defaultRequestTimeout(EventoBundle.Builder builder, Duration timeout) {
        if (timeout == null) return;
        record("defaultRequestTimeout", invoke(builder, "setDefaultRequestTimeout", Duration.class, timeout), timeout);
    }

    /**
     * Require every broker to announce {@code clusterName}. Returns the new
     * configuration, or the unchanged one when the feature is absent.
     */
    public EventoServerMessageBusConfiguration expectedClusterName(EventoServerMessageBusConfiguration config,
                                                                   String clusterName) {
        if (clusterName == null) return config;
        var result = call(config, "withExpectedClusterName", String.class, clusterName);
        record("expectedClusterName", result.isPresent(), clusterName);
        return result.map(EventoServerMessageBusConfiguration.class::cast).orElse(config);
    }

    /**
     * Whether the bundle uses every configured address (G2). Before the fix
     * only the first one is dialled; the lab still passes the whole list.
     */
    public boolean endpointFailover(int addresses) {
        boolean available = classExists("com.evento.application.client.BundleClientConfig$Endpoint");
        record("endpointFailover", available, addresses + " address(es)");
        return available;
    }

    /** {@code CommandGateway.sendIdempotent(Command, String, Metadata, long, TimeUnit)}, absent before G5. */
    private static final Method SEND_IDEMPOTENT = findSendIdempotent();

    private static Method findSendIdempotent() {
        try {
            return CommandGateway.class.getMethod("sendIdempotent", Command.class, String.class, Metadata.class,
                    long.class, TimeUnit.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Whether commands can carry an idempotency key (G5). Recorded once, so {@code /whoami}
     * says whether a gateway's {@code X-Idempotency-Key} reaches the broker.
     */
    public boolean idempotencyKeys() {
        boolean available = SEND_IDEMPOTENT != null;
        synchronized (this) {
            if (outcomes.stream().noneMatch(o -> o.feature().equals("idempotencyKeys"))) {
                record("idempotencyKeys", available, "CommandGateway.sendIdempotent");
            }
        }
        return available;
    }

    /**
     * Send {@code command} with {@code idempotencyKey} when the framework supports keys (G5), so a
     * retry with the same key is answered with the first attempt's event; without the feature, or
     * without a key, a plain send (the pre-G5 behaviour, which the {@code :before} images measure).
     */
    @SuppressWarnings("unchecked")
    public <R> CompletableFuture<R> send(CommandGateway gateway, Command command, String idempotencyKey,
                                         long timeoutMs) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || SEND_IDEMPOTENT == null) {
            return gateway.send(command, timeoutMs, TimeUnit.MILLISECONDS);
        }
        try {
            return (CompletableFuture<R>) SEND_IDEMPOTENT.invoke(gateway, command, idempotencyKey, null, timeoutMs,
                    TimeUnit.MILLISECONDS);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("sendIdempotent not accessible", e);
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new IllegalStateException("sendIdempotent failed", cause);
        }
    }

    // ---- service command outcome store (G5 residual window) ----

    static final String OUTCOME_STORE_CLASS = "com.evento.consumer.state.store.jdbc.JdbcServiceCommandOutcomeStore";
    static final String OUTCOMES_CLASS = "com.evento.common.messaging.outcome.ServiceCommandOutcomes";
    private static final Method OUTCOMES_CURRENT = findOutcomes("current");
    private static final Method OUTCOMES_RECORD_WITH = findOutcomes("recordWith");

    private static Method findOutcomes(String name) {
        try {
            var cls = Class.forName(OUTCOMES_CLASS, false, FrameworkFeatures.class.getClassLoader());
            return name.equals("current") ? cls.getMethod("current")
                    : cls.getMethod("recordWith", java.sql.Connection.class,
                    Class.forName("com.evento.common.modeling.messaging.payload.ServiceEvent", false,
                            FrameworkFeatures.class.getClassLoader()));
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
            return null;
        }
    }

    /**
     * Give the bundle a JDBC outcome store on {@code dataSource} (the service's own database,
     * which must already hold {@code evento_v2_service_outcome}), so a keyed service command
     * whose handler already ran is answered from it instead of running again (the G5 residual
     * window). {@code mode} {@code off} (or a framework without the store) leaves it out.
     *
     * @return whether the store was set
     */
    public boolean serviceOutcomeStore(EventoBundle.Builder builder, javax.sql.DataSource dataSource, String mode) {
        if ("off".equalsIgnoreCase(mode)) {
            record("serviceOutcomeStore", false, "off (EVENTO_SERVICE_OUTCOME_STORE)");
            return false;
        }
        try {
            var cls = Class.forName(OUTCOME_STORE_CLASS, true, FrameworkFeatures.class.getClassLoader());
            var store = cls.getConstructor(javax.sql.DataSource.class,
                    com.evento.consumer.state.store.jdbc.SqlDialect.class)
                    .newInstance(dataSource, com.evento.consumer.state.store.jdbc.SqlDialect.POSTGRES);
            var setter = find(builder.getClass(), "setServiceCommandOutcomeStore", cls);
            if (setter == null) {
                record("serviceOutcomeStore", false, "no EventoBundle.Builder.setServiceCommandOutcomeStore");
                return false;
            }
            setter.invoke(builder, store);
            record("serviceOutcomeStore", true, "jdbc (evento_v2_service_outcome)");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            record("serviceOutcomeStore", false, "no " + OUTCOME_STORE_CLASS);
            return false;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot configure the service outcome store", e);
        }
    }

    /** Whether the framework has the service outcome store (classes present). */
    public static boolean serviceOutcomesAvailable() {
        return OUTCOMES_RECORD_WITH != null && classExists(OUTCOME_STORE_CLASS);
    }

    /**
     * Records {@code result} as the outcome of the keyed service command being handled on this
     * thread, in the transaction of {@code connection}
     * ({@code ServiceCommandOutcomes.current().recordWith}). A no-op on a framework without the
     * store, without a key or without a configured store. A duplicate (another execution of the
     * key committed first) propagates: roll back.
     */
    public static boolean recordServiceOutcome(java.sql.Connection connection, Object result) throws java.sql.SQLException {
        if (OUTCOMES_CURRENT == null || OUTCOMES_RECORD_WITH == null) return false;
        try {
            var current = OUTCOMES_CURRENT.invoke(null);
            return (Boolean) OUTCOMES_RECORD_WITH.invoke(current, connection, result);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("ServiceCommandOutcomes not accessible", e);
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof java.sql.SQLException sql) throw sql;
            if (cause instanceof RuntimeException re) throw re;
            throw new IllegalStateException("recording the service outcome failed", cause);
        }
    }

    static boolean classExists(String name) {
        try {
            Class.forName(name, false, FrameworkFeatures.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Invoke {@code target.name(value)} if such a public method exists; {@code true} if it ran. */
    static boolean invoke(Object target, String name, Class<?> type, Object value) {
        return call(target, name, type, value).isPresent();
    }

    /**
     * Invoke {@code target.name(value)} if it exists. The result is empty when
     * the method is absent; a present method that returns {@code void} or
     * {@code null} yields the target itself. An exception thrown by the method
     * is a real failure and propagates.
     */
    static Optional<Object> call(Object target, String name, Class<?> type, Object value) {
        Method m = find(target.getClass(), name, type);
        if (m == null) return Optional.empty();
        try {
            var r = m.invoke(target, value);
            return Optional.of(r == null ? target : r);
        } catch (IllegalAccessException e) {
            return Optional.empty();
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new IllegalStateException(name + " failed", cause);
        }
    }

    private static Method find(Class<?> cls, String name, Class<?> type) {
        for (var m : cls.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(type)) {
                return m;
            }
        }
        return null;
    }
}
