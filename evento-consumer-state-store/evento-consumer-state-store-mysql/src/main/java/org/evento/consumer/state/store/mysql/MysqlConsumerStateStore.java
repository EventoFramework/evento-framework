package org.evento.consumer.state.store.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.evento.common.messaging.bus.EventoServer;
import org.evento.common.messaging.consumer.ConsumerStateStore;
import org.evento.common.messaging.consumer.StoredSagaState;
import org.evento.common.modeling.state.SagaState;
import org.evento.common.performance.PerformanceService;
import org.evento.common.serialization.ObjectMapperUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MysqlConsumerStateStore extends ConsumerStateStore {

	private static final String CONSUMER_STATE_TABLE = "evento__consumer_state";
	private static final String SAGA_STATE_TABLE = "evento__saga_state";

	private static final String CONSUMER_STATE_DDL = "create table if not exists " + CONSUMER_STATE_TABLE
			+ " (id varchar(255), lastEventSequenceNumber bigint, primary key (id))";
	private static final String SAGA_STATE_DDL = "create table if not exists " + SAGA_STATE_TABLE
			+ " (id int auto_increment, name varchar(255),  state text, primary key (id))";
	private final Connection connection;

	public MysqlConsumerStateStore(
			EventoServer eventoServer,
			PerformanceService performanceService,
			Connection connection) {
		this(eventoServer, performanceService, connection, ObjectMapperUtils.getPayloadObjectMapper(), Executors.newSingleThreadExecutor());
	}

	public MysqlConsumerStateStore(
			EventoServer eventoServer,
			PerformanceService performanceService,
			Connection connection,
			ObjectMapper objectMapper,
			Executor executor) {
		super(eventoServer, performanceService, objectMapper, executor);
		this.connection = connection;
		init();
	}

	public void init() {
		try
		{
            try (var stmt = connection.createStatement()) {
                stmt.execute(CONSUMER_STATE_DDL);
                stmt.execute(SAGA_STATE_DDL);
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
