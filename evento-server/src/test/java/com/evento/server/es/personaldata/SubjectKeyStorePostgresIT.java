package com.evento.server.es.personaldata;

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

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F10: the keys of data subjects in the event store's database, as two servers see them.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class SubjectKeyStorePostgresIT {

    private PostgreSQLContainer postgres;
    private HikariDataSource ds;
    private JdbcTemplate jdbc;

    @BeforeAll
    void start() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl(postgres.getJdbcUrl());
        cfg.setUsername(postgres.getUsername());
        cfg.setPassword(postgres.getPassword());
        cfg.setMaximumPoolSize(16);
        ds = new HikariDataSource(cfg);
        var settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of("classpath:schema.sql"));
        settings.setMode(DatabaseInitializationMode.ALWAYS);
        var init = new AdvisoryLockedSchemaInitializer(ds, settings, true);
        init.setResourceLoader(new DefaultResourceLoader());
        init.initializeDatabase();
        jdbc = new JdbcTemplate(ds);
    }

    @AfterAll
    void stop() {
        if (ds != null) ds.close();
        if (postgres != null) postgres.stop();
    }

    private static String masterKey() {
        var key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static String subject() {
        return "customer-" + UUID.randomUUID();
    }

    @Test
    void everyServerGetsTheSameKeyOfASubject() {
        var a = new SubjectKeyStore(jdbc, "");
        var b = new SubjectKeyStore(jdbc, "");
        var s = subject();

        assertThat(a.key(s, false)).isEmpty();
        byte[] key = a.key(s, true).orElseThrow();
        assertThat(key).hasSize(32);
        assertThat(b.key(s, false)).hasValue(key);
        assertThat(b.key(s, true)).as("creating again keeps the first key").hasValue(key);
    }

    @Test
    void serversCreatingTheKeyOfOneSubjectAtOnceAgreeOnIt() throws Exception {
        var s = subject();
        var stores = List.of(new SubjectKeyStore(jdbc, ""), new SubjectKeyStore(jdbc, ""));
        var tasks = new ArrayList<Callable<String>>();
        for (int i = 0; i < 16; i++) {
            var store = stores.get(i % 2);
            tasks.add(() -> Base64.getEncoder().encodeToString(store.key(s, true).orElseThrow()));
        }
        var seen = new HashSet<String>();
        try (var pool = Executors.newFixedThreadPool(16)) {
            for (Future<String> f : pool.invokeAll(tasks)) seen.add(f.get());
        }
        assertThat(seen).hasSize(1);
    }

    @Test
    void forgettingDeletesTheKeyAndALaterWriteStartsANewOne() {
        var a = new SubjectKeyStore(jdbc, "");
        var b = new SubjectKeyStore(jdbc, "");
        var s = subject();
        byte[] first = a.key(s, true).orElseThrow();

        assertThat(b.forget(s)).isTrue();
        assertThat(a.key(s, false)).isEmpty();
        assertThat(b.forget(s)).isFalse();
        assertThat(a.key(s, true).orElseThrow()).isNotEqualTo(first);
    }

    @Test
    void withAMasterKeyTheTableAloneDoesNotHoldTheKeys() {
        String master = masterKey();
        var a = new SubjectKeyStore(jdbc, master);
        var b = new SubjectKeyStore(jdbc, master);
        var s = subject();
        byte[] key = a.key(s, true).orElseThrow();

        byte[] stored = jdbc.queryForObject("select key_material from es__subject_keys where subject = ?", byte[].class, s);
        assertThat(stored).isNotEqualTo(key);
        assertThat(jdbc.queryForObject("select wrapped from es__subject_keys where subject = ?", Boolean.class, s)).isTrue();
        assertThat(b.key(s, false)).hasValue(key);

        assertThatThrownBy(() -> new SubjectKeyStore(jdbc, "").key(s, false))
                .hasMessageContaining("master-key is not set");
        assertThatThrownBy(() -> new SubjectKeyStore(jdbc, masterKey()).key(s, false))
                .hasMessageContaining("differs");
    }

    @Test
    void keysStoredBeforeAMasterKeyWasSetAreStillRead() {
        var s = subject();
        byte[] key = new SubjectKeyStore(jdbc, "").key(s, true).orElseThrow();
        assertThat(new SubjectKeyStore(jdbc, masterKey()).key(s, false)).hasValue(key);
    }

    @Test
    void aMalformedMasterKeyStopsTheServer() {
        assertThatThrownBy(() -> new SubjectKeyStore(jdbc, "not base64!")).hasMessageContaining("base64");
        assertThatThrownBy(() -> new SubjectKeyStore(jdbc, Base64.getEncoder().encodeToString(new byte[16])))
                .hasMessageContaining("32 bytes");
    }
}
