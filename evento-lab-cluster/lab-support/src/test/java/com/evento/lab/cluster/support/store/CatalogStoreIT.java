package com.evento.lab.cluster.support.store;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The fan-out's idempotency guard on a real Postgres: three regional
 * databases (three databases of one container), one of them unreachable, and
 * redelivery of the same event until it is healthy again.
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogStoreIT {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17-alpine").asCompatibleSubstituteFor("postgres"));

    private HikariDataSource eu;
    private HikariDataSource am;
    private HikariDataSource apac;
    private HikariDataSource down;

    @BeforeAll
    void databases() throws SQLException {
        try (var c = java.sql.DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             var st = c.createStatement()) {
            st.execute("CREATE DATABASE app_am");
            st.execute("CREATE DATABASE app_apac");
        }
        eu = pool(PG.getJdbcUrl());
        am = pool(PG.getJdbcUrl().replace("/" + PG.getDatabaseName(), "/app_am"));
        apac = pool(PG.getJdbcUrl().replace("/" + PG.getDatabaseName(), "/app_apac"));
        // a target whose server is not there at all (closed port), like a stopped app-pg-apac
        down = pool("jdbc:postgresql://127.0.0.1:1/app?connectTimeout=1");
    }

    @AfterAll
    void close() {
        for (var p : List.of(eu, am, apac, down)) p.close();
    }

    private HikariDataSource pool(String url) {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(PG.getUsername());
        cfg.setPassword(PG.getPassword());
        cfg.setMaximumPoolSize(4); // schema init: the lock connection + Flyway's two
        cfg.setConnectionTimeout(2_000);
        cfg.setInitializationFailTimeout(-1);
        return new HikariDataSource(cfg);
    }

    private static CatalogStore store(String name, DataSource ds) {
        return new CatalogStore(name, ds, "it-" + name);
    }

    @Test
    void appliesOncePerTargetAcrossRedeliveriesAndAnOutage() throws Exception {
        var euStore = store("eu", eu);
        var amStore = store("am", am);
        var apacDown = store("apac", down);
        var change = new PriceChange(42, "sku-it", 1999, "EUR", "ALL");

        // apac is down: eu and am get it, the event fails for redelivery
        var degraded = new CatalogFanOut(List.of(euStore, amStore, apacDown));
        assertThatThrownBy(() -> degraded.apply(change)).isInstanceOf(CatalogFanOut.FanOutIncompleteException.class);
        assertThatThrownBy(() -> degraded.apply(change)).isInstanceOf(CatalogFanOut.FanOutIncompleteException.class);

        // apac is back (same name, now reachable): the redelivery completes it
        var healed = new CatalogFanOut(List.of(euStore, amStore, store("apac", apac)));
        var r = healed.apply(change);
        assertThat(r.outcomes()).containsEntry("eu", PriceTarget.Outcome.STALE)
                .containsEntry("am", PriceTarget.Outcome.STALE)
                .containsEntry("apac", PriceTarget.Outcome.APPLIED);

        for (var s : List.of(euStore, amStore, store("apac", apac))) {
            var row = s.find("sku-it").orElseThrow();
            assertThat(row.getAppliedEvents()).as(s.name()).isEqualTo(1);
            assertThat(row.getLastSeq()).isEqualTo(42);
            assertThat(row.getPriceCents()).isEqualTo(1999);
            assertThat(row.getContext()).isEqualTo("ALL");
            assertThat(row.getDatabase()).isEqualTo(s.name());
        }
        // eu saw the event three times and wrote it once
        assertThat(euStore.find("sku-it").orElseThrow().getStaleSkips()).isEqualTo(2);
        assertThat(store("apac", apac).find("sku-it").orElseThrow().getStaleSkips()).isZero();
    }

    @Test
    void newerWinsOlderIsRefused() throws Exception {
        var s = store("eu", eu);
        assertThat(s.apply(new PriceChange(100, "sku-order", 1, "EUR", "ALL"))).isEqualTo(PriceTarget.Outcome.APPLIED);
        assertThat(s.apply(new PriceChange(90, "sku-order", 2, "EUR", "ALL"))).isEqualTo(PriceTarget.Outcome.STALE);
        assertThat(s.apply(new PriceChange(110, "sku-order", 3, "USD", "ALL"))).isEqualTo(PriceTarget.Outcome.APPLIED);
        var row = s.find("sku-order").orElseThrow();
        assertThat(row.getPriceCents()).isEqualTo(3);
        assertThat(row.getCurrency()).isEqualTo("USD");
        assertThat(row.getLastSeq()).isEqualTo(110);
        assertThat(row.getAppliedEvents()).isEqualTo(2);
        assertThat(row.getStaleSkips()).isEqualTo(1);
        assertThat(s.find("nope")).isEmpty();
    }

    @Test
    void labSchemaIsIdempotent() throws Exception {
        LabSchema.initialise(am, true, LabSchema.appDdl());
        LabSchema.initialise(am, true, LabSchema.appDdl());
        try (var c = am.getConnection(); var st = c.createStatement(); var rs = st.executeQuery(
                "SELECT count(*) FROM information_schema.tables WHERE table_name IN "
                        + "('order_view','order_view_global','catalog_price','observed_events','payments',"
                        + "'evento_v2_consumer_state')")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(6);
        }
    }
}
