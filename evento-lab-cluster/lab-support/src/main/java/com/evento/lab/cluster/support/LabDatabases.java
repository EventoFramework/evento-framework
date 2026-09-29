package com.evento.lab.cluster.support;

import com.evento.lab.cluster.support.store.LabSchema;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The connection pools of one deployment: its cell's app database, the
 * consumer-state database (the same pool when it is the same database), the
 * database holding {@code order_view_global}, and the fan-out targets.
 *
 * <p>Pools never connect at construction ({@code initializationFailTimeout=-1}):
 * a remote target that is down when the JVM boots must not keep the bundle
 * from starting, it is retried when it is used.
 */
public final class LabDatabases implements AutoCloseable {

    private final BundleDeployment deployment;
    private final Map<String, HikariDataSource> pools = new LinkedHashMap<>();

    public LabDatabases(BundleDeployment deployment) {
        this.deployment = deployment;
    }

    public synchronized DataSource app() {
        return pool("app-db", deployment.requireAppDb());
    }

    public boolean hasApp() {
        return deployment.appDb() != null;
    }

    public synchronized DataSource consumerState() {
        var cs = deployment.consumerStateDb();
        if (cs == null) {
            throw new IllegalStateException("CONSUMER_STATE_DB_URL or APP_DB_URL is required for bundle "
                    + deployment.bundleId());
        }
        return cs.sameAs(deployment.appDb()) ? app() : pool("consumer-state-db", cs);
    }

    public synchronized DataSource globalView() {
        var gv = deployment.globalViewDb();
        if (gv == null) return app();
        return gv.sameAs(deployment.appDb()) ? app() : pool("global-view-db", gv);
    }

    /**
     * One pool per {@code DATA_TARGETS} entry (a target equal to the app
     * database shares its pool). Empty when no targets are configured.
     */
    public synchronized Map<String, DataSource> targets() {
        var out = new LinkedHashMap<String, DataSource>();
        var app = deployment.appDb();
        for (var e : deployment.dataTargets().entrySet()) {
            var user = app == null ? "postgres" : app.user();
            var password = app == null ? "secret" : app.password();
            var settings = new DbSettings(e.getValue(), user, password, 3);
            out.put(e.getKey(), settings.sameAs(app) ? app() : pool("target-" + e.getKey(), settings));
        }
        return out;
    }

    /**
     * Create the schema the deployment needs: the consumer-state migration on
     * the consumer-state database when the bundle has consumers, and the lab
     * tables on the app database.
     */
    public void initialise(boolean consumers) throws SQLException {
        var appReady = false;
        if (consumers) {
            var cs = consumerState();
            var sameAsApp = hasApp() && cs == app();
            LabSchema.initialise(cs, true, sameAsApp ? LabSchema.appDdl() : List.of());
            appReady = sameAsApp;
        }
        if (hasApp() && !appReady) {
            LabSchema.initialise(app(), false, LabSchema.appDdl());
        }
    }

    private HikariDataSource pool(String name, DbSettings s) {
        return pools.computeIfAbsent(name, n -> {
            var cfg = new HikariConfig();
            cfg.setJdbcUrl(s.url());
            cfg.setUsername(s.user());
            cfg.setPassword(s.password());
            // Each consumer engine pins one connection for its whole cycle
            // (JdbcConsumerLock), so a consumer-state pool must exceed the engines.
            cfg.setMaximumPoolSize(Math.max(3, s.poolSize()));
            // fan-out targets and diagnostic readers are used rarely and often over the WAN:
            // keep no idle connection to them
            cfg.setMinimumIdle(n.startsWith("target-") ? 0 : 1);
            cfg.setConnectionTimeout(10_000);
            cfg.setValidationTimeout(3_000);
            cfg.setInitializationFailTimeout(-1);
            cfg.setPoolName(n);
            return new HikariDataSource(cfg);
        });
    }

    public synchronized List<String> describe() {
        var out = new ArrayList<String>();
        pools.forEach((n, p) -> out.add(n + " -> " + p.getJdbcUrl()));
        return out;
    }

    @Override
    public synchronized void close() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
