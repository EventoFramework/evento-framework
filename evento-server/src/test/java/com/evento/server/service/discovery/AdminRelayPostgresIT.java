package com.evento.server.service.discovery;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerProcessDeadQueueRequestMessage;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerResponseMessage;
import com.evento.server.bus.BusFacade;
import com.evento.server.config.AdvisoryLockedSchemaInitializer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * G17: a console action on a consumer connected to another server goes through the database to
 * that server, which runs it and writes the answer back. Two relays on one database stand for
 * two servers; server B holds the consumer's instance.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class AdminRelayPostgresIT {

    private PostgreSQLContainer postgres;
    private HikariDataSource ds;
    private JdbcTemplate jdbc;
    private AdminRelay serverA;
    private AdminRelay serverB;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread bWorker;

    @BeforeAll
    void start() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl(postgres.getJdbcUrl());
        cfg.setUsername(postgres.getUsername());
        cfg.setPassword(postgres.getPassword());
        cfg.setMaximumPoolSize(6);
        ds = new HikariDataSource(cfg);
        var settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of("classpath:schema.sql"));
        settings.setMode(DatabaseInitializationMode.ALWAYS);
        var init = new AdvisoryLockedSchemaInitializer(ds, settings, true);
        init.setResourceLoader(new DefaultResourceLoader());
        init.initializeDatabase();
        jdbc = new JdbcTemplate(ds);
        jdbc.update("insert into core__bundle_instance (instance_id, bundle_id, server_instance_id, updated_at) "
                + "values ('orders-1', 'orders', 'server-b', now())");

        serverA = new AdminRelay(jdbc, mock(BusFacade.class),
                new BundlePresence(jdbc, "server-a", Duration.ofMinutes(3)), 180_000);
        serverB = new AdminRelay(jdbc, mock(BusFacade.class),
                new BundlePresence(jdbc, "server-b", Duration.ofMinutes(3)), 180_000);
        // Server B's scheduled poll.
        bWorker = Thread.ofVirtual().start(() -> {
            while (running.get()) {
                serverB.runRelayed();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
    }

    @AfterAll
    void stop() throws Exception {
        running.set(false);
        bWorker.join(2_000);
        serverA.close();
        serverB.close();
        ds.close();
        postgres.stop();
    }

    @Test
    void theServerHoldingTheInstanceRunsTheActionAndAnswers() throws Exception {
        serverB.serve((consumerId, body, bus) -> {
            var ok = new ConsumerResponseMessage();
            ok.setSuccess(body instanceof ConsumerProcessDeadQueueRequestMessage && consumerId.equals("orders_P_1_EU"));
            return CompletableFuture.completedFuture(ok);
        });

        assertThat(serverA.ownerOf(Set.of("orders-1"))).contains("server-b");
        assertThat(serverB.ownerOf(Set.of("orders-1"))).as("not to itself").isEmpty();

        var answer = serverA.send("server-b", "orders_P_1_EU",
                new ConsumerProcessDeadQueueRequestMessage("orders_P_1_EU", ComponentType.Projector))
                .get(10, TimeUnit.SECONDS);
        assertThat(answer).isInstanceOf(ConsumerResponseMessage.class);
        assertThat(((ConsumerResponseMessage) answer).isSuccess()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from core__admin_relay", Integer.class))
                .as("the row is gone once answered").isZero();
    }

    @Test
    void aFailureOnTheOtherServerReachesTheCaller() {
        serverB.serve((consumerId, body, bus) -> CompletableFuture.failedFuture(
                new IllegalStateException("the instance left")));

        assertThatThrownBy(() -> serverA.send("server-b", "orders_P_1_EU",
                new ConsumerProcessDeadQueueRequestMessage("orders_P_1_EU", ComponentType.Projector))
                .get(10, TimeUnit.SECONDS))
                .hasMessageContaining("server-b could not run the action")
                .hasMessageContaining("the instance left");
    }

    @Test
    void anInstanceWhoseServerStoppedHeartbeatingHasNoOwner() {
        jdbc.update("insert into core__bundle_instance (instance_id, bundle_id, server_instance_id, updated_at) "
                + "values ('stale-1', 'orders', 'server-c', now() - interval '1 hour')");
        assertThat(serverA.ownerOf(Set.of("stale-1"))).isEmpty();
    }
}
