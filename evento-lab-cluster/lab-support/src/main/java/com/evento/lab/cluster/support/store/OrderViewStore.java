package com.evento.lab.cluster.support.store;

import com.evento.lab.cluster.api.view.OrderViewDto;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * One order read-model table: {@link #REGIONAL_TABLE} in a cell's database
 * (one table for every region of the cell, told apart by {@code context}) or
 * {@link #GLOBAL_TABLE}, written by the wildcard deployment.
 *
 * <p>Every write is an idempotent upsert keyed by order id that applies only
 * when the event's sequence number is above the row's {@code last_seq}, so a
 * replay (at-least-once redelivery after a lock handover or a checkpoint
 * rewind) is harmless. A skipped replay is still counted in
 * {@code stale_skips}: that counter is the evidence of redelivery.
 *
 * <p>{@code context} is the context of the event that last touched the row,
 * stored as delivered and never filtered again: if the server's context
 * match leaks another context in (G15, LIKE with an unescaped {@code _}), the
 * row shows it and I4 catches it.
 */
public class OrderViewStore {

    public static final String REGIONAL_TABLE = "order_view";
    public static final String GLOBAL_TABLE = "order_view_global";

    public enum Change { CREATED, ITEM_ADDED, COMPLETED, PAID }

    private final DataSource dataSource;
    private final String table;
    private final String writer;
    private final String createdSql;
    private final String itemSql;
    private final String statusSql;
    private final String paidSql;
    private final String skipSql;
    private final String findSql;

    public OrderViewStore(DataSource dataSource, String table, String writer) {
        if (!table.matches("[a-z_]+")) throw new IllegalArgumentException("unsafe table name " + table);
        this.dataSource = dataSource;
        this.table = table;
        this.writer = writer;
        this.createdSql = """
                INSERT INTO %1$s AS t (order_id, context, status, created_seq, last_seq, applied_events, updated_by)
                VALUES (?, ?, 'CREATED', ?, ?, 1, ?)
                ON CONFLICT (order_id) DO UPDATE SET
                    context = EXCLUDED.context,
                    status = CASE WHEN t.status = 'UNKNOWN' THEN 'CREATED' ELSE t.status END,
                    created_seq = COALESCE(t.created_seq, EXCLUDED.created_seq),
                    last_seq = EXCLUDED.last_seq,
                    applied_events = t.applied_events + 1,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = now()
                WHERE t.last_seq < EXCLUDED.last_seq""".formatted(table);
        this.itemSql = """
                INSERT INTO %1$s AS t (order_id, context, status, last_seq, item_count, item_qty, applied_events, updated_by)
                VALUES (?, ?, 'UNKNOWN', ?, 1, ?, 1, ?)
                ON CONFLICT (order_id) DO UPDATE SET
                    context = EXCLUDED.context,
                    item_count = t.item_count + 1,
                    item_qty = t.item_qty + EXCLUDED.item_qty,
                    last_seq = EXCLUDED.last_seq,
                    applied_events = t.applied_events + 1,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = now()
                WHERE t.last_seq < EXCLUDED.last_seq""".formatted(table);
        this.statusSql = """
                INSERT INTO %1$s AS t (order_id, context, status, last_seq, applied_events, updated_by)
                VALUES (?, ?, 'COMPLETED', ?, 1, ?)
                ON CONFLICT (order_id) DO UPDATE SET
                    context = EXCLUDED.context,
                    status = 'COMPLETED',
                    last_seq = EXCLUDED.last_seq,
                    applied_events = t.applied_events + 1,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = now()
                WHERE t.last_seq < EXCLUDED.last_seq""".formatted(table);
        this.paidSql = """
                INSERT INTO %1$s AS t (order_id, context, status, payment_status, last_seq, applied_events, updated_by)
                VALUES (?, ?, 'UNKNOWN', 'PAID', ?, 1, ?)
                ON CONFLICT (order_id) DO UPDATE SET
                    payment_status = 'PAID',
                    last_seq = EXCLUDED.last_seq,
                    applied_events = t.applied_events + 1,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = now()
                WHERE t.last_seq < EXCLUDED.last_seq""".formatted(table);
        this.skipSql = "UPDATE %s SET stale_skips = stale_skips + 1 WHERE order_id = ?".formatted(table);
        this.findSql = """
                SELECT order_id, context, status, item_count, item_qty, payment_status, created_seq,
                       last_seq, applied_events, stale_skips, updated_by
                FROM %s WHERE order_id = ?""".formatted(table);
    }

    public static String createTableSql(String table) {
        return """
                CREATE TABLE IF NOT EXISTS %s (
                    order_id        VARCHAR(200) PRIMARY KEY,
                    context         VARCHAR(100) NOT NULL,
                    status          VARCHAR(32)  NOT NULL,
                    item_count      INT          NOT NULL DEFAULT 0,
                    item_qty        INT          NOT NULL DEFAULT 0,
                    payment_status  VARCHAR(32),
                    created_seq     BIGINT,
                    last_seq        BIGINT       NOT NULL,
                    applied_events  INT          NOT NULL DEFAULT 0,
                    stale_skips     INT          NOT NULL DEFAULT 0,
                    updated_by      VARCHAR(200),
                    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
                )""".formatted(table);
    }

    public String table() {
        return table;
    }

    /**
     * Apply one event to the view.
     *
     * @return {@code true} if applied, {@code false} if it was a stale replay
     */
    public boolean apply(Change change, String orderId, String context, long seq, int qty) throws SQLException {
        try (var con = dataSource.getConnection()) {
            int updated;
            switch (change) {
                case CREATED -> {
                    try (var ps = con.prepareStatement(createdSql)) {
                        ps.setString(1, orderId);
                        ps.setString(2, context);
                        ps.setLong(3, seq);
                        ps.setLong(4, seq);
                        ps.setString(5, writer);
                        updated = ps.executeUpdate();
                    }
                }
                case ITEM_ADDED -> {
                    try (var ps = con.prepareStatement(itemSql)) {
                        ps.setString(1, orderId);
                        ps.setString(2, context);
                        ps.setLong(3, seq);
                        ps.setInt(4, qty);
                        ps.setString(5, writer);
                        updated = ps.executeUpdate();
                    }
                }
                case COMPLETED -> {
                    try (var ps = con.prepareStatement(statusSql)) {
                        ps.setString(1, orderId);
                        ps.setString(2, context);
                        ps.setLong(3, seq);
                        ps.setString(4, writer);
                        updated = ps.executeUpdate();
                    }
                }
                case PAID -> {
                    try (var ps = con.prepareStatement(paidSql)) {
                        ps.setString(1, orderId);
                        ps.setString(2, context);
                        ps.setLong(3, seq);
                        ps.setString(4, writer);
                        updated = ps.executeUpdate();
                    }
                }
                default -> throw new IllegalArgumentException(String.valueOf(change));
            }
            if (updated == 0) {
                try (var ps = con.prepareStatement(skipSql)) {
                    ps.setString(1, orderId);
                    ps.executeUpdate();
                }
                return false;
            }
            return true;
        }
    }

    public Optional<OrderViewDto> find(String orderId) throws SQLException {
        try (var con = dataSource.getConnection(); var ps = con.prepareStatement(findSql)) {
            ps.setString(1, orderId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private static OrderViewDto map(ResultSet rs) throws SQLException {
        var v = new OrderViewDto();
        v.setOrderId(rs.getString("order_id"));
        v.setContext(rs.getString("context"));
        v.setStatus(rs.getString("status"));
        v.setItemCount(rs.getInt("item_count"));
        v.setItemQty(rs.getInt("item_qty"));
        v.setPaymentStatus(rs.getString("payment_status"));
        long created = rs.getLong("created_seq");
        v.setCreatedSeq(rs.wasNull() ? null : created);
        v.setLastSeq(rs.getLong("last_seq"));
        v.setAppliedEvents(rs.getInt("applied_events"));
        v.setStaleSkips(rs.getInt("stale_skips"));
        v.setUpdatedBy(rs.getString("updated_by"));
        return v;
    }
}
