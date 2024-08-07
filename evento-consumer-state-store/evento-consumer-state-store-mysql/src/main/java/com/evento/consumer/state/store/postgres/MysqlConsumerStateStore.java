package com.evento.consumer.state.store.postgres;

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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * MysqlConsumerStateStore is a class that extends the ConsumerStateStore abstract class and implements
 * methods for storing and retrieving consumer and saga state using MySQL database.
 */
public class MysqlConsumerStateStore extends ConsumerStateStore {

	private final String CONSUMER_STATE_TABLE;
	private final String SAGA_STATE_TABLE;
	private final String DEAD_EVENT_TABLE;

	private final String CONSUMER_STATE_DDL;
	private final String SAGA_STATE_DDL;
	private final String DEAD_EVENT_DDL;
	private final Connection connection;

	/**
	 * Implementation of the ConsumerStateStore interface that stores the consumer state in MySQL database.
     * @param eventoServer an instance of evento server connection
     * @param performanceService  an instance of performance service
     * @param connection a MySQL java connection
     */
	public MysqlConsumerStateStore(
			EventoServer eventoServer,
			PerformanceService performanceService,
			Connection connection) {
		this(eventoServer, performanceService, connection, ObjectMapperUtils.getPayloadObjectMapper(), Executors.newVirtualThreadPerTaskExecutor(),
				"","");
	}

	/**
	 * Implementation of the ConsumerStateStore interface that stores the consumer state in MySQL database.
	 * @param eventoServer an instance of evento server connection
	 * @param performanceService  an instance of performance service
	 * @param connection a MySQL java connection
	 * @param tablePrefix prefix to add to tables
	 * @param tableSuffix suffix to add to tables
	 */
	public MysqlConsumerStateStore(
			EventoServer eventoServer,
			PerformanceService performanceService,
			Connection connection,
			String tablePrefix,
			String tableSuffix) {
		this(eventoServer, performanceService, connection, ObjectMapperUtils.getPayloadObjectMapper(), Executors.newVirtualThreadPerTaskExecutor(),
				tablePrefix, tableSuffix);
	}

	/**
	 * Represents a consumer state store implementation that stores the consumer state in a MySQL database.
     * @param eventoServer an instance of evento server connection
     * @param performanceService an instance of performance service
     * @param connection a MySQL java connection
     * @param objectMapper an object mapper to manage serialization
     * @param observerExecutor observer executor
     */
	public MysqlConsumerStateStore(
			EventoServer eventoServer,
			PerformanceService performanceService,
			Connection connection,
			ObjectMapper objectMapper,
			Executor observerExecutor,
			String tablePrefix,
			String tableSuffix) {
		super(eventoServer, performanceService, objectMapper, observerExecutor);
		this.connection = connection;
		this.CONSUMER_STATE_TABLE = tablePrefix + "evento__consumer_state" + tableSuffix;
		this.SAGA_STATE_TABLE = tablePrefix + "evento__saga_state" + tableSuffix;
		this.DEAD_EVENT_TABLE = tablePrefix + "evento__dead_event" + tableSuffix;
		this.CONSUMER_STATE_DDL =  "create table if not exists " + CONSUMER_STATE_TABLE
				+ " (id varchar(255), lastEventSequenceNumber bigint, primary key (id))";
		this.SAGA_STATE_DDL = "create table if not exists " + SAGA_STATE_TABLE
				+ " (id int auto_increment, name varchar(255),  state text, primary key (id))";
		this.DEAD_EVENT_DDL =  "create table if not exists " + DEAD_EVENT_TABLE
				+ " (consumer varchar(255), eventSequenceNumber bigint, eventName varchar(255), toProcess boolean, primary key (consumer, eventSequenceNumber))";
		init();
	}

	/**
	 * Initializes the MySQL consumer state store.
	 * <p>
	 * This method creates the necessary database tables for storing consumer and saga states in a MySQL database.
	 * It should be called once before using the consumer state store.
	 *
	 * @throws RuntimeException if an exception occurs during the initialization process.
	 */
	public void init() {
		try
		{
            try (var stmt = connection.createStatement()) {
                stmt.execute(CONSUMER_STATE_DDL);
                stmt.execute(SAGA_STATE_DDL);
                stmt.execute(DEAD_EVENT_DDL);
            }
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}


	@Override
	protected void removeSagaState(Long sagaId) throws Exception {
		var stmt = connection.prepareStatement("delete from " + SAGA_STATE_TABLE + " where id = ?");
		stmt.setLong(1, sagaId);
		if (stmt.executeUpdate() == 0) throw new RuntimeException("Saga state delete error");
	}

	@Override
	protected void leaveExclusiveZone(String consumerId) {
		try
		{
            try (var stmt = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                stmt.setString(1, String.valueOf(consumerId.hashCode()));
                var resultSet = stmt.executeQuery();
                resultSet.next();
                var status = resultSet.getInt(1);
                if (resultSet.wasNull() || status == 0) throw new IllegalMonitorStateException();
            }
		} catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
	}

	@Override
	protected boolean enterExclusiveZone(String consumerId) {
		try
		{
            try (var stmt = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                stmt.setString(1, String.valueOf(consumerId.hashCode()));
                var resultSet = stmt.executeQuery();
                resultSet.next();
                if (resultSet.wasNull()) return false;
                var status = resultSet.getInt(1);

                return status == 1;
            }
		} catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
	}

	@Override
	protected Long getLastEventSequenceNumber(String consumerId) throws Exception {

		var stmt = connection.prepareStatement("SELECT lastEventSequenceNumber from " + CONSUMER_STATE_TABLE + " where id = ?");
		stmt.setString(1, consumerId);
		var resultSet = stmt.executeQuery();
		if (!resultSet.next()) return null;
		return resultSet.getLong(1);
	}

	@Override
	protected void addEventToDeadEventQueue(String consumerId, PublishedEvent event) throws Exception {
		var q = "insert into " + DEAD_EVENT_TABLE + " (consumer, eventSequenceNumber, eventName, toProcess) value (?, ?, ?, true, ?)";
		var stmt = connection.prepareStatement(q);
		stmt.setString(1, consumerId);
		stmt.setLong(2, event.getEventSequenceNumber());
		stmt.setString(3, event.getEventName());
		if (stmt.executeUpdate() == 0) throw new RuntimeException("Insert into Dead Event Queue error");
	}

	@Override
	protected void setLastEventSequenceNumber(String consumerId, Long eventSequenceNumber) throws Exception {
		var q = "insert into " + CONSUMER_STATE_TABLE + " (id, lastEventSequenceNumber) value (?, ?) on duplicate key update lastEventSequenceNumber = ?";
		var stmt = connection.prepareStatement(q);
		stmt.setString(1, consumerId);
		stmt.setLong(2, eventSequenceNumber);
		stmt.setLong(3, eventSequenceNumber);
		if (stmt.executeUpdate() == 0) throw new RuntimeException("Consumer state update error");
	}

	@Override
	protected StoredSagaState getSagaState(String sagaName,
										   String associationProperty,
										   String associationValue) throws Exception {
		var stmt = connection.prepareStatement("select id, state from " + SAGA_STATE_TABLE + " where name = ? and JSON_EXTRACT(state, concat('$[1].associations[1].', ?)) = ?");
		stmt.setString(1, sagaName);
		stmt.setString(2, associationProperty);
		stmt.setString(3, associationValue);
		var resultSet = stmt.executeQuery();
		if (!resultSet.next()) return new StoredSagaState(null, null);
		var state = getObjectMapper().readValue(resultSet.getString(2), SagaState.class);
		return new StoredSagaState(resultSet.getLong(1), state);

	}

	@Override
	protected void setSagaState(Long id, String sagaName, SagaState sagaState) throws Exception {
        java.sql.PreparedStatement stmt;
        if (id == null)
		{
            stmt = connection.prepareStatement("insert into " + SAGA_STATE_TABLE + " (name, state) value (?, ?)");
			stmt.setString(1, sagaName);
			var serializedSagaState = getObjectMapper().writeValueAsString(sagaState);
			stmt.setString(2, serializedSagaState);
        } else
		{
            stmt = connection.prepareStatement("update " + SAGA_STATE_TABLE + " set state = ? where id = ?");
			stmt.setLong(2, id);
			var serializedSagaState = getObjectMapper().writeValueAsString(sagaState);
			stmt.setString(1, serializedSagaState);
        }
        if (stmt.executeUpdate() == 0) throw new RuntimeException("Saga state update error");
    }
}
