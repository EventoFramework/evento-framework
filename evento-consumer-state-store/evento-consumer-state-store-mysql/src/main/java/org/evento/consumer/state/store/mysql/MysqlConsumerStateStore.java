package org.evento.consumer.state.store.mysql;

import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.messaging.consumer.ConsumerStateStore;
import org.evento.common.messaging.consumer.StoredSagaState;
import org.evento.common.modeling.state.SagaState;
import org.evento.common.performance.RemotePerformanceService;
import org.evento.common.serialization.ObjectMapperUtils;

import java.sql.Connection;
import java.sql.SQLException;

public class MysqlConsumerStateStore extends ConsumerStateStore {

	private static final String CONSUMER_STATE_TABLE = "er__consumer_state";
	private static final String SAGA_STATE_TABLE = "er__saga_state";

	private static final String CONSUMER_STATE_DDL = "create table if not exists " + CONSUMER_STATE_TABLE
			+ " (id varchar(255), lastEventSequenceNumber bigint, primary key (id))";
	private static final String SAGA_STATE_DDL = "create table if not exists " + SAGA_STATE_TABLE
			+ " (id int auto_increment, name varchar(255),  state text, primary key (id))";
	private final Connection connection;

	public MysqlConsumerStateStore(
			MessageBus messageBus,
			String bundleId,
			String serverNodeName,
			Connection connection) {
		super(messageBus, bundleId, serverNodeName, new RemotePerformanceService(messageBus, serverNodeName));
		this.connection = connection;
		init();
	}

	public void init() {
		try
		{
			var stmt = connection.createStatement();
			try
			{
				stmt.execute(CONSUMER_STATE_DDL);
				stmt.execute(SAGA_STATE_DDL);
			} finally
			{
				stmt.close();
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
			var stmt = connection.prepareStatement("SELECT RELEASE_LOCK(?)");
			try
			{
				stmt.setString(1, String.valueOf(consumerId.hashCode()));
				var resultSet = stmt.executeQuery();
				resultSet.next();
				var status = resultSet.getInt(1);
				if (resultSet.wasNull() || status == 0) throw new IllegalMonitorStateException();
			} finally
			{
				stmt.close();
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
			var stmt = connection.prepareStatement("SELECT GET_LOCK(?, 0)");
			try
			{
				stmt.setString(1, String.valueOf(consumerId.hashCode()));
				var resultSet = stmt.executeQuery();
				resultSet.next();
				if (resultSet.wasNull()) return false;
				var status = resultSet.getInt(1);

				return status == 1;
			} finally
			{
				stmt.close();
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
		var state = ObjectMapperUtils.getPayloadObjectMapper().readValue(resultSet.getString(2), SagaState.class);
		return new StoredSagaState(resultSet.getLong(1), state);

	}

	@Override
	protected void setSagaState(Long id, String sagaName, SagaState sagaState) throws Exception {
		if (id == null)
		{
			var stmt = connection.prepareStatement("insert into " + SAGA_STATE_TABLE + " (name, state) value (?, ?)");
			stmt.setString(1, sagaName);
			var serializedSagaState = ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(sagaState);
			stmt.setString(2, serializedSagaState);
			if (stmt.executeUpdate() == 0) throw new RuntimeException("Saga state update error");
		} else
		{
			var stmt = connection.prepareStatement("update " + SAGA_STATE_TABLE + " set state = ? where id = ?");
			stmt.setLong(2, id);
			var serializedSagaState = ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(sagaState);
			stmt.setString(1, serializedSagaState);
			if (stmt.executeUpdate() == 0) throw new RuntimeException("Saga state update error");
		}
	}
}
