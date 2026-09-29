package com.evento.lab.cluster.store;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * The two side-effect tables of the lab. Both are idempotent by key but count
 * every repeated delivery, so a duplicate side effect is visible to the
 * invariant checker instead of being silently absorbed:
 * <ul>
 *   <li>{@code payments.attempts > 1}: the payment service ran twice for one order.</li>
 *   <li>{@code observed_events.deliveries > 1}: an observer saw the same event twice.</li>
 * </ul>
 */
public class SideEffectStore {

    private final DataSource dataSource;
    private final String writer;

    public SideEffectStore(DataSource dataSource, String writer) {
        this.dataSource = dataSource;
        this.writer = writer;
    }

    /** @return the attempt number for this order (1 on the first call). */
    public int recordPayment(String orderId, String paymentId, String context) throws SQLException {
        try (var con = dataSource.getConnection(); var ps = con.prepareStatement("""
                INSERT INTO payments AS p (order_id, payment_id, context, first_handler, last_handler)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (order_id) DO UPDATE SET
                    attempts = p.attempts + 1,
                    last_handler = EXCLUDED.last_handler,
                    last_at = now()
                RETURNING attempts""")) {
            ps.setString(1, orderId);
            ps.setString(2, paymentId);
            ps.setString(3, context);
            ps.setString(4, writer);
            ps.setString(5, writer);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void recordObserved(long seq, String eventName, String orderId, String context) throws SQLException {
        try (var con = dataSource.getConnection(); var ps = con.prepareStatement("""
                INSERT INTO observed_events AS o (event_seq, event_name, order_id, context, first_observer, last_observer)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_seq) DO UPDATE SET
                    deliveries = o.deliveries + 1,
                    last_observer = EXCLUDED.last_observer,
                    last_seen = now()""")) {
            ps.setLong(1, seq);
            ps.setString(2, eventName);
            ps.setString(3, orderId);
            ps.setString(4, context);
            ps.setString(5, writer);
            ps.setString(6, writer);
            ps.executeUpdate();
        }
    }
}
