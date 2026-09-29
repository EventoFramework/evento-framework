package com.evento.server.es.pg;

import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.serialization.ObjectMapperUtils;
import com.evento.common.utils.PgDistributedLock;
import com.evento.server.es.EventStore;
import com.evento.server.es.EventStoreMode;
import com.evento.server.es.eventstore.EventStoreEntry;
import com.evento.server.es.eventstore.EventStoreRepository;
import com.evento.server.es.snapshot.SnapshotRepository;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CPES append path (G9), the context filter (G15) and the append lock's round-trip cost
 * against a real Postgres, with the production schema applied by the production initializer.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class EventStorePostgresIT {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgresItSupport.newContainer();

    /** A concrete domain event for fixtures; only its class name and context matter. */
    public static class RegionEvent extends DomainEvent {
    }

    private HikariDataSource serverA;
    private HikariDataSource serverB;

    @BeforeAll
    void setup() throws Exception {
        PostgresItSupport.createDatabase(POSTGRES, "es_it");
        String url = PostgresItSupport.jdbcUrl(POSTGRES, "es_it");
        serverA = PostgresItSupport.dataSource(POSTGRES, url, 10);
        serverB = PostgresItSupport.dataSource(POSTGRES, url, 10);
        PostgresItSupport.initializer(serverA, true).initializeDatabase();
    }

    @AfterAll
    void teardown() {
        if (serverA != null) serverA.close();
        if (serverB != null) serverB.close();
    }

    private static EventStore eventStore(DataSource ds, EventStoreRepository repo, SnapshotRepository snapshots)
            throws Exception {
        return new EventStore(repo, snapshots, new JdbcTemplate(ds), ds,
                1024, 1024, 150_000, 150_000, 69, EventStoreMode.CPES);
    }

    private static DomainEventMessage event(String context) {
        var msg = new DomainEventMessage(new RegionEvent());
        msg.setContext(context);
        return msg;
    }

    @Test
    @Timeout(180)
    void concurrentAppendsFromTwoServersAreUniqueGaplessAndCommittedInSequenceOrder() throws Exception {
        new JdbcTemplate(serverA).update("truncate es__events");
        var storeA = eventStore(serverA, null, null);
        var storeB = eventStore(serverB, null, null);

        int threadsPerServer = 6;
        int appendsPerThread = 40;
        int total = 2 * threadsPerServer * appendsPerThread;

        // A CPES consumer tails by "seq > last" with no fetch delay. That is only safe if a
        // reader can never see seq N+1 before N is visible: every poll must return a gapless
        // continuation of what it saw before.
        var appendersDone = new AtomicBoolean(false);
        var tailViolations = new ConcurrentLinkedQueue<String>();
        var tailJdbc = new JdbcTemplate(serverB);
        var tailer = Thread.ofPlatform().start(() -> {
            long last = tailJdbc.queryForObject("select coalesce(max(event_sequence_number), 0) from es__events", Long.class);
            long seen = 0;
            while (!appendersDone.get() || seen < total) {
                List<Long> batch = tailJdbc.queryForList(
                        "select event_sequence_number from es__events where event_sequence_number > ? order by 1",
                        Long.class, last);
                for (long seq : batch) {
                    if (last != 0 && seq != last + 1) {
                        tailViolations.add("saw " + seq + " right after " + last);
                    }
                    last = seq;
                    seen++;
                }
                if (appendersDone.get() && batch.isEmpty()) break;
            }
        });

        var start = new CountDownLatch(1);
        var failures = new ConcurrentLinkedQueue<Throwable>();
        var appended = new ConcurrentLinkedQueue<Long>();
        List<Thread> appenders = new ArrayList<>();
        for (var store : List.of(storeA, storeB)) {
            for (int t = 0; t < threadsPerServer; t++) {
                int thread = t;
                appenders.add(Thread.ofPlatform().start(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < appendsPerThread; i++) {
                            appended.add(store.publishEvent(event(thread % 2 == 0 ? "EUROPA" : "SUD_AMERICA"),
                                    "agg-" + thread + "-" + i));
                        }
                    } catch (Throwable e) {
                        failures.add(e);
                    }
                }));
            }
        }
        start.countDown();
        for (var t : appenders) t.join();
        appendersDone.set(true);
        tailer.join(60_000);

        assertThat(failures).isEmpty();
        assertThat(new HashSet<>(appended)).as("returned sequence numbers are unique").hasSize(total);

        var rows = new JdbcTemplate(serverA).queryForList(
                "select event_sequence_number as seq, pg_xact_commit_timestamp(xmin) as committed " +
                        "from es__events order by event_sequence_number");
        assertThat(rows).hasSize(total);
        long first = ((Number) rows.getFirst().get("seq")).longValue();
        long lastSeq = ((Number) rows.getLast().get("seq")).longValue();
        assertThat(lastSeq - first + 1).as("no gaps: nothing failed, so no number was burned").isEqualTo(total);
        assertThat(new HashSet<>(appended)).containsExactlyInAnyOrderElementsOf(
                rows.stream().map(r -> ((Number) r.get("seq")).longValue()).toList());

        Timestamp previous = null;
        for (Map<String, Object> row : rows) {
            var committed = (Timestamp) row.get("committed");
            assertThat(committed).as("track_commit_timestamp is on").isNotNull();
            if (previous != null) {
                assertThat(committed).as("seq %s committed no earlier than its predecessor", row.get("seq"))
                        .isAfterOrEqualTo(previous);
            }
            previous = committed;
        }
        assertThat(tailViolations).as("a tailing reader only ever sees a gapless prefix").isEmpty();
    }

    @Test
    @Timeout(180)
    void appendCostsOneRoundTripInsteadOfFour() throws Throwable {
        // Well above local RTT and Windows' timer granularity, so elapsed / delay ~ round trips.
        long delayMs = 100;
        try (var proxy = new DelayProxy(POSTGRES.getHost(), POSTGRES.getMappedPort(5432), delayMs);
             var slow = PostgresItSupport.dataSource(POSTGRES,
                     "jdbc:postgresql://127.0.0.1:" + proxy.port() + "/es_it", 2)) {
            var store = eventStore(slow, null, null);
            double now = roundTrips(delayMs, () -> store.publishEvent(event("EUROPA"), "rtt-new"));

            // The pre-G9 append, reproduced verbatim: session lock on its own connection,
            // nextval, INSERT, unlock -- each an autocommit statement.
            var legacyLock = new PgDistributedLock(slow);
            var jdbc = new JdbcTemplate(slow);
            var mapper = ObjectMapperUtils.getPayloadObjectMapper();
            double before = roundTrips(delayMs, () -> legacyLock.lockedArea("es-lock", () -> {
                long id = jdbc.queryForObject("SELECT nextval('event_sequence_number_serial')", Long.class);
                var msg = event("EUROPA");
                jdbc.update("INSERT INTO es__events (event_sequence_number, aggregate_id, event_message, event_name, context) " +
                                "values (?, ?, ?, ?, ?)",
                        id, "rtt-old", mapper.writeValueAsString(msg), msg.getEventName(), msg.getContext());
            }));

            System.out.printf("event=cpes_append_round_trips before=%.2f after=%.2f delayMs=%d%n", before, now, delayMs);
            assertThat(now).as("one statement, one implicit transaction").isLessThan(1.75);
            assertThat(before).as("the legacy path, for reference").isGreaterThan(3.4);
        }
    }

    private static double roundTrips(long delayMs, ThrowingRunnable append) throws Throwable {
        for (int i = 0; i < 3; i++) append.run(); // warm the pool and the prepared statements
        int n = 10;
        long started = System.nanoTime();
        for (int i = 0; i < n; i++) append.run();
        double perAppendMs = (System.nanoTime() - started) / 1_000_000.0 / n;
        return perAppendMs / delayMs;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    @Test
    void contextFilterTreatsUnderscoreAndPercentLiterally() throws Exception {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            // Wrapped so closing the context does not close the shared pool.
            ctx.registerBean(DataSource.class, () -> new DelegatingDataSource(serverA));
            ctx.register(JpaConfig.class);
            ctx.refresh();
            var jdbc = new JdbcTemplate(serverA);
            jdbc.update("truncate es__events");
            var store = eventStore(serverA, ctx.getBean(EventStoreRepository.class), ctx.getBean(SnapshotRepository.class));

            for (String c : List.of("SUD_AMERICA", "SUDXAMERICA", "AUSTRALIA_NZ", "AUSTRALIAxNZ", "EUROPA",
                    "50%OFF", "50XOFF", "a\\b", "HEY!", "HEY!X")) {
                store.publishEvent(event(c), "agg-" + c);
            }

            assertThat(contexts(store.fetchEvents("SUD_AMERICA", -1L, 100))).containsExactly("SUD_AMERICA");
            assertThat(contexts(store.fetchEvents("AUSTRALIA_NZ", -1L, 100))).containsExactly("AUSTRALIA_NZ");
            assertThat(contexts(store.fetchEvents("50%OFF", -1L, 100))).containsExactly("50%OFF");
            assertThat(contexts(store.fetchEvents("a\\b", -1L, 100))).containsExactly("a\\b");
            assertThat(contexts(store.fetchEvents("SUD_*", -1L, 100))).containsExactly("SUD_AMERICA");
            assertThat(contexts(store.fetchEvents("SUD*", -1L, 100))).containsExactly("SUD_AMERICA", "SUDXAMERICA");
            assertThat(contexts(store.fetchEvents("HEY!", -1L, 100))).containsExactly("HEY!");
            assertThat(contexts(store.fetchEvents("*", -1L, 100))).hasSize(10);
            assertThat(contexts(store.fetchEvents("SUD_AMERICA", -1L, 100,
                    List.of(event("SUD_AMERICA").getEventName())))).containsExactly("SUD_AMERICA");
        }
    }

    private static List<String> contexts(List<EventStoreEntry> entries) {
        return entries.stream().map(EventStoreEntry::getContext).toList();
    }

    /** Just enough JPA for the two event-store repositories, named as in Spring Boot. */
    @Configuration
    @EnableJpaRepositories(basePackageClasses = {EventStoreRepository.class, SnapshotRepository.class})
    static class JpaConfig {

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var emf = new LocalContainerEntityManagerFactoryBean();
            emf.setDataSource(dataSource);
            emf.setPackagesToScan("com.evento.server.es.eventstore", "com.evento.server.es.snapshot");
            emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            var props = new Properties();
            props.put("hibernate.hbm2ddl.auto", "none");
            props.put("hibernate.physical_naming_strategy",
                    "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
            emf.setJpaProperties(props);
            return emf;
        }

        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }
    }
}
