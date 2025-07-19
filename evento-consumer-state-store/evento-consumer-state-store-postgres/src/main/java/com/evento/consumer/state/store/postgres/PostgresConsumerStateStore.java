package com.evento.consumer.state.store.postgres;

import com.evento.common.messaging.consumer.DeadPublishedEvent;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.messaging.consumer.ConsumerStateStore;
import com.evento.common.messaging.consumer.StoredSagaState;
import com.evento.common.modeling.state.SagaState;
import com.evento.common.performance.PerformanceService;
import com.evento.common.serialization.ObjectMapperUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * PostgresqlConsumerStateStore is an implementation of the ConsumerStateStore interface that stores the consumer state in a PostgresSQL database.
 */
public class PostgresConsumerStateStore extends ConsumerStateStore {

    private final String CONSUMER_STATE_TABLE;
    private final String SAGA_STATE_TABLE;
    private final String DEAD_EVENT_TABLE;

    private final String CONSUMER_STATE_DDL;
    private final String SAGA_STATE_DDL;
    private final String DEAD_EVENT_DDL;

    private Connection conn;
    protected final Supplier<Connection> connectionFactory;

    /**
     * Implementation of the ConsumerStateStore interface that stores the consumer state in PostgresSQL database.
     *
     * @param eventoServer       an instance of evento server connection
     * @param performanceService an instance of performance service
     * @param connectionFactory  a PostgresSQL java connection Factory
     */
    public PostgresConsumerStateStore(
            EventoServer eventoServer,
            PerformanceService performanceService,
            Supplier<Connection> connectionFactory) {
        this(eventoServer, performanceService, connectionFactory, ObjectMapperUtils.getPayloadObjectMapper(), Executors.newCachedThreadPool(),
                "", "");
    }

    /**
     * Implementation of the ConsumerStateStore interface that stores the consumer state in PostgresSQL database.
     *
     * @param eventoServer       an instance of evento server connection
     * @param performanceService an instance of performance service
     * @param connectionFactory  a PostgresSQL java connection factory
     * @param tablePrefix        prefix to add to tables
     * @param tableSuffix        suffix to add to tables
     */
    public PostgresConsumerStateStore(
            EventoServer eventoServer,
            PerformanceService performanceService,
            Supplier<Connection> connectionFactory,
            String tablePrefix,
            String tableSuffix) {
        this(eventoServer, performanceService, connectionFactory, ObjectMapperUtils.getPayloadObjectMapper(), Executors.newCachedThreadPool(),
                tablePrefix, tableSuffix);
    }

    /**
     * Represents a consumer state store implementation that stores the consumer state in a PostgresSQL database.
     *
     * @param eventoServer       an instance of evento server connection
     * @param performanceService an instance of performance service
     * @param connectionFactory  a PostgresSQL java connection factory
     * @param objectMapper       an object mapper to manage serialization
     * @param observerExecutor   observer executor
     */
    public PostgresConsumerStateStore(
            EventoServer eventoServer,
            PerformanceService performanceService,
            Supplier<Connection> connectionFactory,
            ObjectMapper objectMapper,
            Executor observerExecutor,
            String tablePrefix,
            String tableSuffix) {
        super(eventoServer, performanceService, objectMapper, observerExecutor);
        this.connectionFactory = connectionFactory;
        this.CONSUMER_STATE_TABLE = tablePrefix + "evento__consumer_state" + tableSuffix;
        this.SAGA_STATE_TABLE = tablePrefix + "evento__saga_state" + tableSuffix;
        this.DEAD_EVENT_TABLE = tablePrefix + "evento__dead_event" + tableSuffix;
        this.CONSUMER_STATE_DDL = "create table if not exists " + CONSUMER_STATE_TABLE
                + " (id varchar(255), lastEventSequenceNumber bigint, primary key (id))";
        this.SAGA_STATE_DDL = "create table if not exists " + SAGA_STATE_TABLE
                + " (id serial, name varchar(255),  state text, primary key (id))";
        this.DEAD_EVENT_DDL = "create table if not exists " + DEAD_EVENT_TABLE
                + " (consumerId varchar(255), eventSequenceNumber bigint, eventName varchar(255), retry boolean, deadAt timestamp, event json, aggregateId varchar(255), context varchar(255), exception json, primary key (consumerId, eventSequenceNumber))";
        init();
    }


    protected synchronized Connection getConnection() {
        try {
            if (conn == null || !conn.isValid(3)) {
                conn = connectionFactory.get();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return conn;
    }

    /**
     * Initializes the PostgresSQL consumer state store.
     * <p>
     * This method creates the necessary database tables for storing consumer and saga states in a PostgresSQL database.
     * It should be called once before using the consumer state store.
     *
     * @throws RuntimeException if an exception occurs during the initialization process.
     */
    public void init() {
        try {
            try (var stmt = getConnection().createStatement()) {
                stmt.execute(CONSUMER_STATE_DDL);
                stmt.execute(SAGA_STATE_DDL);
                stmt.execute(DEAD_EVENT_DDL);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    protected void removeSagaState(Long sagaId) throws Exception {
        try(var stmt = getConnection().prepareStatement("delete from " + SAGA_STATE_TABLE + " where id = ?")){
			stmt.setLong(1, sagaId);
			if (stmt.executeUpdate() == 0) throw new RuntimeException("Saga state delete error");
		}

    }

    private final HashMap<String, Connection> lockConnectionMap = new HashMap<>();


    protected synchronized Connection getConnection(String id) {
        var lockConn = lockConnectionMap.get(id);
        try {
            if (lockConn == null || !lockConn.isValid(3)) {
                lockConn = connectionFactory.get();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        lockConnectionMap.put(id, lockConn);
        return lockConn;
    }

    @Override
    protected void leaveExclusiveZone(String consumerId) {
        try {
            try (var stmt = getConnection(consumerId).prepareStatement("SELECT pg_advisory_unlock(hashtext('" + consumerId + "'))")) {
                var resultSet = stmt.executeQuery();
                resultSet.next();
                if (resultSet.wasNull()) throw new IllegalMonitorStateException();
                var status = resultSet.getBoolean(1);
                if (!status) throw new IllegalMonitorStateException("Lock key: " + consumerId);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean enterExclusiveZone(String consumerId) {
        try {
            try (var stmt = getConnection(consumerId).prepareStatement("SELECT pg_advisory_lock(hashtext('" + consumerId + "'))")) {
                var resultSet = stmt.executeQuery();
                resultSet.next();
                if (resultSet.wasNull()) return false;
                var status = resultSet.getString(1);
                return "".equals(status);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Long getLastEventSequenceNumber(String consumerId) throws Exception {
        try (var stmt = getConnection().prepareStatement("SELECT lastEventSequenceNumber from " + CONSUMER_STATE_TABLE + " where id = ?")) {
            stmt.setString(1, consumerId);
            var resultSet = stmt.executeQuery();
            if (!resultSet.next()) return null;
            return resultSet.getLong(1);
        }

    }

    @Override
    protected void setLastEventSequenceNumber(String consumerId, Long eventSequenceNumber) throws Exception {
        var q = "insert into " + CONSUMER_STATE_TABLE + " (id, lastEventSequenceNumber) values (?, ?) on conflict (id) do update set lasteventsequencenumber = ?";
        try (var stmt = getConnection().prepareStatement(q)) {
            stmt.setString(1, consumerId);
            stmt.setLong(2, eventSequenceNumber);
            stmt.setLong(3, eventSequenceNumber);
            if (stmt.executeUpdate() == 0) throw new RuntimeException("Consumer state update error");
        }

    }

    @Override
    public void addEventToDeadEventQueue(String consumerId, PublishedEvent event, Throwable exception) throws Exception {
        var q = "insert into " + DEAD_EVENT_TABLE + "  (consumerId, eventSequenceNumber, eventName, retry, deadAt, event, aggregateId, context, exception) values (?, ?, ?, false,?,?::json,?,?,?::json)";
        try (var stmt = getConnection().prepareStatement(q)) {
            stmt.setString(1, consumerId);
            stmt.setLong(2, event.getEventSequenceNumber());
            stmt.setString(3, event.getEventName());
            stmt.setTimestamp(4, new Timestamp(ZonedDateTime.now().toInstant().toEpochMilli()));
            stmt.setString(5, getObjectMapper().writeValueAsString(event));
            stmt.setString(6, event.getAggregateId());
            stmt.setString(7, event.getEventMessage().getContext());
            stmt.setString(8, getObjectMapper().writeValueAsString(new ExceptionWrapper(exception)));
            if (stmt.executeUpdate() == 0) throw new RuntimeException("Insert into Dead Event Queue error");
        }

    }

    @Override
    public void removeEventFromDeadEventQueue(String consumerId, long eventSequenceNumber) throws Exception {
        var q = "delete from " + DEAD_EVENT_TABLE + " where consumerId = ? and eventSequenceNumber = ?";
        try (var stmt = getConnection().prepareStatement(q)) {
            stmt.setString(1, consumerId);
            stmt.setLong(2, eventSequenceNumber);
            stmt.executeUpdate();
        }
    }

    @Override
    protected Collection<PublishedEvent> getEventsToReprocessFromDeadEventQueue(String consumerId) throws Exception {
        var q = "select event from " + DEAD_EVENT_TABLE + " where consumerId = ? and retry = true";
        try (var stmt = getConnection().prepareStatement(q)) {
            stmt.setString(1, consumerId);
            var rs = stmt.executeQuery();
            var events = new ArrayList<PublishedEvent>();
            while (rs.next()) {
                events.add(getObjectMapper().readValue(rs.getString("event"), PublishedEvent.class));
            }
            return events;
        }
    }

    @Override
    public Collection<DeadPublishedEvent> getEventsFromDeadEventQueue(String consumerId) throws Exception {
        var q = "select * from " + DEAD_EVENT_TABLE + " where consumerId = ?";
        try (var stmt = getConnection().prepareStatement(q)) {
            stmt.setString(1, consumerId);
            var rs = stmt.executeQuery();
            var events = new ArrayList<DeadPublishedEvent>();
            while (rs.next()) {
                events.add(
                        new DeadPublishedEvent(
                                rs.getString("consumerId"),
                                rs.getString("eventName"),
                                rs.getString("aggregateId"),
                                rs.getString("context"),
                                rs.getLong("eventSequenceNumber") + "",
                                getObjectMapper().readValue(rs.getString("event"), PublishedEvent.class),
                                rs.getBoolean("retry"),
                                getObjectMapper().readValue(rs.getString("exception"), ExceptionWrapper.class),
                                ZonedDateTime.ofInstant(rs.getTimestamp("deadAt").toInstant(), ZoneId.systemDefault())
                        ));

            }
            return events;
        }
    }

    @Override
    public void setRetryDeadEvent(String consumerId, long eventSequenceNumber, boolean retry) throws Exception {
        var q = "update " + DEAD_EVENT_TABLE + " set retry = ? where consumerId = ? and eventSequenceNumber = ?";
        try (var stmt = getConnection().prepareStatement(q)) {
            stmt.setBoolean(1, retry);
            stmt.setString(2, consumerId);
            stmt.setLong(3, eventSequenceNumber);
            stmt.executeUpdate();
        }
    }


    @Override
    public StoredSagaState getSagaState(String sagaName,
                                        String associationProperty,
                                        String associationValue) throws Exception {
        try (var stmt = getConnection().prepareStatement("select id, state from " + SAGA_STATE_TABLE + " where name = ? and json_extract_path_text(state::json->1->'associations'->1,?) = ?")) {
            stmt.setString(1, sagaName);
            stmt.setString(2, associationProperty);
            stmt.setString(3, associationValue);
            var resultSet = stmt.executeQuery();
            if (!resultSet.next()) return new StoredSagaState(null, null);
            var state = getObjectMapper().readValue(resultSet.getString(2), SagaState.class);
            return new StoredSagaState(resultSet.getLong(1), state);
        }


    }

    @Override
    public Collection<StoredSagaState> getSagaStates(String sagaName) throws Exception {
        try (var stmt = getConnection().prepareStatement("select id, state from " + SAGA_STATE_TABLE + " where name = ?")) {
            stmt.setString(1, sagaName);
            var resultSet = stmt.executeQuery();
            var response = new ArrayList<StoredSagaState>();
            while (!resultSet.next()) {
                var state = getObjectMapper().readValue(resultSet.getString(2), SagaState.class);
                response.add(new StoredSagaState(resultSet.getLong(1), state));
            }
            return response;
        }
    }

    @Override
    public void setSagaState(Long id, String sagaName, SagaState sagaState) throws Exception {
        try (java.sql.PreparedStatement stmt = id == null ? getConnection().prepareStatement("insert into " + SAGA_STATE_TABLE + " (name, state) values (?, ?)")
                : getConnection().prepareStatement("update " + SAGA_STATE_TABLE + " set state = ? where id = ?")) {
            if (id == null) {
                stmt.setString(1, sagaName);
                var serializedSagaState = getObjectMapper().writeValueAsString(sagaState);
                stmt.setString(2, serializedSagaState);
            } else {
                stmt.setLong(2, id);
                var serializedSagaState = getObjectMapper().writeValueAsString(sagaState);
                stmt.setString(1, serializedSagaState);
            }
            if (stmt.executeUpdate() == 0) throw new RuntimeException("Saga state update error");
        }
    }
}
