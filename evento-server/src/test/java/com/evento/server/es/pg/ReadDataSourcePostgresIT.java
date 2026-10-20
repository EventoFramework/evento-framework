package com.evento.server.es.pg;

import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.serialization.ObjectMapperUtils;
import com.evento.server.es.EventStore;
import com.evento.server.es.EventStoreMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.Serializable;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G21: with a read datasource the consumers' fetches read there, and only there; under APES it is
 * refused. A second database with other events stands for the replica, so a row read proves where
 * the fetch went.
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class ReadDataSourcePostgresIT {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgresItSupport.newContainer();

    public static class Noted extends DomainEvent implements Serializable {
        public String note;

        public Noted() {
        }

        Noted(String note) {
            this.note = note;
        }
    }

    private static void insert(JdbcTemplate jdbc, long seq, String note, String context) throws Exception {
        String json = ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(new DomainEventMessage(new Noted(note)));
        jdbc.update("insert into es__events (event_sequence_number, aggregate_id, event_message, event_name, context) "
                + "values (?, 'a-1', ?, 'Noted', ?)", seq, json, context);
    }

    @Test
    void fetchesReadFromTheReplicaWhenOneIsConfigured() throws Exception {
        PostgresItSupport.createDatabase(POSTGRES, "read_primary");
        PostgresItSupport.createDatabase(POSTGRES, "read_replica");
        try (var primary = PostgresItSupport.dataSource(POSTGRES, PostgresItSupport.jdbcUrl(POSTGRES, "read_primary"), 4);
             var replica = PostgresItSupport.dataSource(POSTGRES, PostgresItSupport.jdbcUrl(POSTGRES, "read_replica"), 4)) {
            PostgresItSupport.initializer(primary, true).initializeDatabase();
            PostgresItSupport.initializer(replica, true).initializeDatabase();
            insert(new JdbcTemplate(primary), 1, "on the primary", "EU");
            insert(new JdbcTemplate(replica), 1, "on the replica", "EU");
            insert(new JdbcTemplate(replica), 2, "another context", "US");

            var store = new EventStore(null, null, new JdbcTemplate(primary), primary, 16, 16, 1000, 1000, 69,
                    EventStoreMode.CPES);
            try {
                store.configureReadDataSource(PostgresItSupport.jdbcUrl(POSTGRES, "read_replica"),
                        POSTGRES.getUsername(), POSTGRES.getPassword(), 2);
                assertThat(store.readsFromReplica()).isTrue();

                var fetched = store.fetchEvents("EU", 0L, 10);
                assertThat(fetched).hasSize(1);
                assertThat(((Noted) fetched.getFirst().getEventMessage().getPayload()).note).isEqualTo("on the replica");
                assertThat(store.fetchEvents("*", 0L, 10)).hasSize(2);
                assertThat(store.fetchEvents("*", 0L, 10, List.of("Other"))).isEmpty();
                assertThat(store.fetchEvents("*", 0L, 10, List.of("Noted"))).hasSize(2);
            } finally {
                store.close();
            }
        }
    }

    @Test
    void underApesTheReplicaIsRefused() throws Exception {
        PostgresItSupport.createDatabase(POSTGRES, "read_apes");
        try (var primary = PostgresItSupport.dataSource(POSTGRES, PostgresItSupport.jdbcUrl(POSTGRES, "read_apes"), 4)) {
            PostgresItSupport.initializer(primary, true).initializeDatabase();
            @SuppressWarnings("deprecation")
            var store = new EventStore(null, null, new JdbcTemplate(primary), primary, 16, 16, 1000, 1000, 69,
                    EventStoreMode.APES);
            try {
                store.configureReadDataSource(PostgresItSupport.jdbcUrl(POSTGRES, "read_apes"),
                        POSTGRES.getUsername(), POSTGRES.getPassword(), 2);
                assertThat(store.readsFromReplica()).isFalse();
            } finally {
                store.close();
            }
        }
    }
}
