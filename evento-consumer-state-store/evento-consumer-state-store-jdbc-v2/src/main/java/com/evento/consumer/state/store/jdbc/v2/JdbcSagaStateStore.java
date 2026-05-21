package com.evento.consumer.state.store.jdbc.v2;

import com.evento.common.messaging.consumer.StoredSagaState;
import com.evento.common.messaging.consumer.v2.SagaStateStore;
import com.evento.common.modeling.state.SagaState;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC {@link SagaStateStore} backed by {@code evento_v2_saga_state}.
 *
 * <p>The full {@link SagaState} is serialized into the {@code state} column
 * with the user-supplied {@link ObjectMapper} — which must enable default
 * typing or otherwise capture the concrete subclass so reads can deserialize.
 * The flat {@code associations} JSON object is serialized with a separate
 * plain-mapper view (constructed from
 * {@link SagaState#getAssociations()}) so JSON-path lookup stays simple.
 */
public final class JdbcSagaStateStore implements SagaStateStore {

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final ObjectMapper objectMapper;

    public JdbcSagaStateStore(DataSource dataSource, SqlDialect dialect, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<StoredSagaState> findByAssociation(String sagaName,
                                                       String associationProperty,
                                                       String associationValue) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(dialect.sagaFindByAssociationSql())) {
            stmt.setString(1, sagaName);
            stmt.setString(2, associationProperty);
            stmt.setString(3, associationValue);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                long id = rs.getLong(1);
                SagaState state = objectMapper.readValue(rs.getString(2), SagaState.class);
                return Optional.of(new StoredSagaState(id, state));
            }
        } catch (Exception e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException(
                    "saga findByAssociation failed for " + sagaName, e);
        }
    }

    @Override
    public Collection<StoredSagaState> findAll(String sagaName) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(
                     "SELECT id, state FROM evento_v2_saga_state WHERE saga_name = ?")) {
            stmt.setString(1, sagaName);
            try (ResultSet rs = stmt.executeQuery()) {
                var out = new ArrayList<StoredSagaState>();
                while (rs.next()) {
                    long id = rs.getLong(1);
                    SagaState state = objectMapper.readValue(rs.getString(2), SagaState.class);
                    out.add(new StoredSagaState(id, state));
                }
                return out;
            }
        } catch (Exception e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException("saga findAll failed for " + sagaName, e);
        }
    }

    @Override
    public long insert(String sagaName, SagaState state) {
        String stateJson = writeJson(state);
        String assocJson = writeAssociationsJson(state);
        boolean ended = state.isEnded();
        try (Connection c = dataSource.getConnection()) {
            return switch (dialect) {
                case POSTGRES -> insertPostgres(c, sagaName, stateJson, assocJson, ended);
                case MYSQL -> insertMysql(c, sagaName, stateJson, assocJson, ended);
            };
        } catch (SQLException e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException(
                    "saga insert failed for " + sagaName, e);
        }
    }

    private long insertPostgres(Connection c, String sagaName, String stateJson, String assocJson, boolean ended)
            throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(dialect.sagaInsertSql())) {
            stmt.setString(1, sagaName);
            stmt.setString(2, stateJson);
            stmt.setString(3, assocJson);
            stmt.setBoolean(4, ended);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) throw new SQLException("saga insert: missing returning id");
                return rs.getLong(1);
            }
        }
    }

    private long insertMysql(Connection c, String sagaName, String stateJson, String assocJson, boolean ended)
            throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(dialect.sagaInsertSql(), Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, sagaName);
            stmt.setString(2, stateJson);
            stmt.setString(3, assocJson);
            stmt.setBoolean(4, ended);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("saga insert: missing generated key");
                return keys.getLong(1);
            }
        }
    }

    @Override
    public void update(long id, SagaState state) {
        String stateJson = writeJson(state);
        String assocJson = writeAssociationsJson(state);
        boolean ended = state.isEnded();
        var sql = switch (dialect) {
            case POSTGRES -> "UPDATE evento_v2_saga_state SET state = ?::jsonb, associations = ?::jsonb, "
                    + "ended = ?, updated_at = now() WHERE id = ?";
            case MYSQL -> "UPDATE evento_v2_saga_state SET state = ?, associations = ?, "
                    + "ended = ? WHERE id = ?";
        };
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, stateJson);
            stmt.setString(2, assocJson);
            stmt.setBoolean(3, ended);
            stmt.setLong(4, id);
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new JdbcConsumerStateStore.ConsumerStateStoreException(
                        "saga update: no row with id=" + id);
            }
        } catch (SQLException e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException(
                    "saga update failed for id=" + id, e);
        }
    }

    @Override
    public void delete(long id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(
                     "DELETE FROM evento_v2_saga_state WHERE id = ?")) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException(
                    "saga delete failed for id=" + id, e);
        }
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException("saga JSON serialize failed", e);
        }
    }

    private String writeAssociationsJson(SagaState state) {
        try {
            Map<String, String> assocs = state.getAssociations();
            // Always use a plain mapper view for associations so JSON-path
            // lookups stay simple (no Jackson polymorphic wrapping).
            return new ObjectMapper().writeValueAsString(assocs);
        } catch (Exception e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException(
                    "saga associations serialize failed", e);
        }
    }
}
