package com.evento.consumer.state.store.jdbc.v2;

/**
 * SQL flavor selector. Supports Postgres and MySQL — the two dialects users
 * have historically run. Each constant exposes the dialect-specific bits the
 * stores need at runtime.
 *
 * <p>The migration location is the only resource path that varies between
 * dialects; all schema differences (JSONB vs JSON, advisory locks vs
 * GET_LOCK, upsert idioms) are encoded in this enum so the JDBC stores stay
 * dialect-agnostic.
 */
public enum SqlDialect {

    POSTGRES(
            "classpath:db/migration/postgres/v2",
            "INSERT INTO evento_v2_consumer_state(consumer_id, kind, last_sequence, version) "
                    + "VALUES (?, ?, ?, 1) ON CONFLICT (consumer_id) DO NOTHING"
    ),
    MYSQL(
            "classpath:db/migration/mysql/v2",
            "INSERT IGNORE INTO evento_v2_consumer_state(consumer_id, kind, last_sequence, version) "
                    + "VALUES (?, ?, ?, 1)"
    );

    private final String migrationLocation;
    private final String insertIfAbsentSql;

    SqlDialect(String migrationLocation, String insertIfAbsentSql) {
        this.migrationLocation = migrationLocation;
        this.insertIfAbsentSql = insertIfAbsentSql;
    }

    public String migrationLocation() {
        return migrationLocation;
    }

    public String insertIfAbsentSql() {
        return insertIfAbsentSql;
    }

    /** Cast a {@code ?} bind for JSON columns. Postgres needs {@code ::jsonb}; MySQL takes plain text. */
    public String jsonBind() {
        return switch (this) {
            case POSTGRES -> "?::jsonb";
            case MYSQL -> "?";
        };
    }

    /** "Insert or replace by PK" for the dead-event queue. */
    public String deadEventUpsertSql() {
        return switch (this) {
            case POSTGRES -> "INSERT INTO evento_v2_dead_event(consumer_id, event_sequence_number, event_name, "
                    + "aggregate_id, context, event, exception, retry, dead_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, false, now()) "
                    + "ON CONFLICT (consumer_id, event_sequence_number) DO UPDATE SET "
                    + "event_name = EXCLUDED.event_name, aggregate_id = EXCLUDED.aggregate_id, "
                    + "context = EXCLUDED.context, event = EXCLUDED.event, exception = EXCLUDED.exception, "
                    + "retry = false, dead_at = now()";
            case MYSQL -> "INSERT INTO evento_v2_dead_event(consumer_id, event_sequence_number, event_name, "
                    + "aggregate_id, context, event, exception, retry, dead_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, false, CURRENT_TIMESTAMP) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "event_name = VALUES(event_name), aggregate_id = VALUES(aggregate_id), "
                    + "context = VALUES(context), event = VALUES(event), exception = VALUES(exception), "
                    + "retry = false, dead_at = CURRENT_TIMESTAMP";
        };
    }

    /** Lookup a saga instance by a single (property, value) association. */
    public String sagaFindByAssociationSql() {
        return switch (this) {
            case POSTGRES -> "SELECT id, state FROM evento_v2_saga_state "
                    + "WHERE saga_name = ? AND associations ->> ? = ? LIMIT 1";
            case MYSQL -> "SELECT id, state FROM evento_v2_saga_state "
                    + "WHERE saga_name = ? AND JSON_UNQUOTE(JSON_EXTRACT(associations, "
                    + "CONCAT('$.', ?))) = ? LIMIT 1";
        };
    }

    /** Insert a brand-new saga instance — engine reads back the generated id. */
    public String sagaInsertSql() {
        return switch (this) {
            case POSTGRES -> "INSERT INTO evento_v2_saga_state(saga_name, state, associations, ended) "
                    + "VALUES (?, ?::jsonb, ?::jsonb, ?) RETURNING id";
            case MYSQL -> "INSERT INTO evento_v2_saga_state(saga_name, state, associations, ended) "
                    + "VALUES (?, ?, ?, ?)";
        };
    }
}
