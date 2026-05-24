package com.evento.consumer.state.store.jdbc;

import com.evento.common.messaging.consumer.DeadPublishedEvent;
import com.evento.common.messaging.consumer.DeadEventQueue;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * JDBC {@link DeadEventQueue} backed by {@code evento_v2_dead_event}. Stores
 * the serialized {@link PublishedEvent} + {@link ExceptionWrapper} so the
 * dashboard can render dead entries and operators can mark them for retry.
 *
 * <p>Uses {@link SqlDialect#deadEventUpsertSql()} so re-failing the same event
 * just refreshes the row instead of duplicating it.
 */
public final class JdbcDeadEventQueue implements DeadEventQueue {

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final ObjectMapper objectMapper;

    public JdbcDeadEventQueue(DataSource dataSource, SqlDialect dialect, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.objectMapper = objectMapper;
    }

    @Override
    public void add(String consumerId, PublishedEvent event, Throwable cause) {
        String eventJson = writeJson(event);
        String exceptionJson = writeJson(new ExceptionWrapper(cause));
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(dialect.deadEventUpsertSql())) {
            stmt.setString(1, consumerId);
            stmt.setLong(2, event.getEventSequenceNumber());
            stmt.setString(3, event.getEventName());
            stmt.setString(4, event.getAggregateId());
            stmt.setString(5, event.getEventMessage() != null ? event.getEventMessage().getContext() : null);
            stmt.setString(6, eventJson);
            stmt.setString(7, exceptionJson);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException(
                    "DLQ add failed for (" + consumerId + ", " + event.getEventSequenceNumber() + ")", e);
        }
    }

    @Override
    public void remove(String consumerId, long eventSequenceNumber) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(
                     "DELETE FROM evento_v2_dead_event WHERE consumer_id = ? AND event_sequence_number = ?")) {
            stmt.setString(1, consumerId);
            stmt.setLong(2, eventSequenceNumber);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException("DLQ remove failed", e);
        }
    }

    @Override
    public Collection<PublishedEvent> getRetriable(String consumerId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(
                     "SELECT event FROM evento_v2_dead_event WHERE consumer_id = ? AND retry = true")) {
            stmt.setString(1, consumerId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<PublishedEvent> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(objectMapper.readValue(rs.getString(1), PublishedEvent.class));
                }
                return out;
            }
        } catch (Exception e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException(
                    "DLQ getRetriable failed for " + consumerId, e);
        }
    }

    @Override
    public Collection<DeadPublishedEvent> getAll(String consumerId) {
        var sql = "SELECT event_sequence_number, event_name, aggregate_id, context, event, "
                + "exception, retry, dead_at FROM evento_v2_dead_event WHERE consumer_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, consumerId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<DeadPublishedEvent> out = new ArrayList<>();
                while (rs.next()) {
                    long seq = rs.getLong(1);
                    String name = rs.getString(2);
                    String aggregateId = rs.getString(3);
                    String context = rs.getString(4);
                    PublishedEvent event = objectMapper.readValue(rs.getString(5), PublishedEvent.class);
                    ExceptionWrapper exception = objectMapper.readValue(rs.getString(6), ExceptionWrapper.class);
                    boolean retry = rs.getBoolean(7);
                    ZonedDateTime deadAt = rs.getTimestamp(8) == null ? null
                            : ZonedDateTime.ofInstant(rs.getTimestamp(8).toInstant(), ZoneId.systemDefault());
                    out.add(new DeadPublishedEvent(consumerId, name, aggregateId, context,
                            String.valueOf(seq), event, retry, exception, deadAt));
                }
                return out;
            }
        } catch (Exception e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException(
                    "DLQ getAll failed for " + consumerId, e);
        }
    }

    @Override
    public void setRetry(String consumerId, long eventSequenceNumber, boolean retry) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(
                     "UPDATE evento_v2_dead_event SET retry = ? "
                             + "WHERE consumer_id = ? AND event_sequence_number = ?")) {
            stmt.setBoolean(1, retry);
            stmt.setString(2, consumerId);
            stmt.setLong(3, eventSequenceNumber);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException("DLQ setRetry failed", e);
        }
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException("DLQ JSON serialize failed", e);
        }
    }
}
