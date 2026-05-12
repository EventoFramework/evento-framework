package com.evento.demo.api.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZonedDateTime;

public class StressDB {

    public StressDB() {
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://localhost:5432/evento-demo", "postgres", "secret");
    }

    public void init() {
        try (Connection conn = getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("create table if not exists stress_test (" +
                    "stress_id varchar(255), " +
                    "instance_id bigint, " +
                    "sent_at timestamp, " +
                    "handled_at timestamp, " +
                    "processed_at timestamp, " +
                    "received_at timestamp, " +
                    "primary key (stress_id, instance_id))");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void createStress(String stressIdentifier, long instances) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "insert into stress_test (stress_id, instance_id) values (?, ?) on conflict do nothing")) {
            for (long i = 0; i < instances; i++) {
                ps.setString(1, stressIdentifier);
                ps.setLong(2, i);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void stressInstanceSent(String stressIdentifier, long instance, ZonedDateTime timestamp) {
        updateTimestamp(stressIdentifier, instance, timestamp, "sent_at");
    }

    public void stressInstanceHandled(String stressIdentifier, long instance, ZonedDateTime timestamp) {
        updateTimestamp(stressIdentifier, instance, timestamp, "handled_at");
    }

    public void stressInstanceProcessed(String stressIdentifier, long instance, ZonedDateTime timestamp) {
        updateTimestamp(stressIdentifier, instance, timestamp, "processed_at");
    }

    public void stressInstanceReceived(String stressIdentifier, long instance, ZonedDateTime timestamp) {
        updateTimestamp(stressIdentifier, instance, timestamp, "received_at");
    }

    private void updateTimestamp(String stressIdentifier, long instance, ZonedDateTime timestamp, String column) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "update stress_test set " + column + " = ? where stress_id = ? and instance_id = ?")) {
            ps.setTimestamp(1, Timestamp.from(timestamp.toInstant()));
            ps.setString(2, stressIdentifier);
            ps.setLong(3, instance);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
