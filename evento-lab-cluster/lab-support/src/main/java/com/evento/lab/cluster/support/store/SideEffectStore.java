package com.evento.lab.cluster.support.store;

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
 *
 * <p>Both live in the database of the cell that ran the side effect (the
 * command handler's or the observer's local app database), so the invariant
 * checker sums them over the three cell databases: a retry that ran in
 * another cell still shows up as a second attempt.
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
        try {
            return recordPayment(orderId, paymentId, context, (con, attempt) -> attempt);
        } catch (SQLException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Work done in the payment's transaction, before it commits. */
    @FunctionalInterface
    public interface InPaymentTransaction<T> {
        T apply(java.sql.Connection connection, int attempt) throws Exception;
    }

    /**
     * Records the payment and runs {@code beforeCommit} in the same transaction: the payment
     * service builds its event there and records it as the command's outcome (G5 inbox), so the
     * payment row and the outcome commit together or not at all. Anything thrown rolls both back.
     */
    public <T> T recordPayment(String orderId, String paymentId, String context, InPaymentTransaction<T> beforeCommit)
            throws Exception {
        try (var con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                int attempt;
                try (var ps = con.prepareStatement("""
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
                        attempt = rs.getInt(1);
                    }
                }
                T result = beforeCommit.apply(con, attempt);
                con.commit();
                return result;
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    /**
     * @param lane the observer lane (its bundle id): two different observer
     *             bundles seeing one event is two notifications by design, not
     *             a duplicate, so deliveries are counted per lane
     */
    public void recordObserved(String lane, long seq, String eventName, String orderId, String context)
            throws SQLException {
        try (var con = dataSource.getConnection(); var ps = con.prepareStatement("""
                INSERT INTO observed_events AS o (lane, event_seq, event_name, order_id, context, first_observer, last_observer)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (lane, event_seq) DO UPDATE SET
                    deliveries = o.deliveries + 1,
                    last_observer = EXCLUDED.last_observer,
                    last_seen = now()""")) {
            ps.setString(1, lane);
            ps.setLong(2, seq);
            ps.setString(3, eventName);
            ps.setString(4, orderId);
            ps.setString(5, context);
            ps.setString(6, writer);
            ps.setString(7, writer);
            ps.executeUpdate();
        }
    }
}
