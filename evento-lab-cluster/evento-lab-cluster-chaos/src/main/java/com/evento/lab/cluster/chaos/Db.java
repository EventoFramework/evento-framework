package com.evento.lab.cluster.chaos;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * JDBC reads of the event-store DB and the app DB, through the host-published
 * ports with a multi-host URL ({@code targetServerType=primary}), so reads
 * follow a promoted replica after S8/S9.
 */
public class Db {

    private final LabConfig config;

    public Db(LabConfig config) {
        this.config = config;
    }

    public Connection es() throws SQLException {
        return connect(config.esJdbcUrl());
    }

    public Connection app() throws SQLException {
        return connect(config.appJdbcUrl());
    }

    private Connection connect(String url) throws SQLException {
        var p = new Properties();
        p.setProperty("user", config.dbUser());
        p.setProperty("password", config.dbPassword());
        DriverManager.setLoginTimeout(10);
        return DriverManager.getConnection(url, p);
    }

    /** Orders are scoped by id prefix; guard against LIKE metacharacters in it. */
    static String likePrefix(String prefix) {
        return prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public Rows.Snapshot snapshot(String prefix) {
        var errors = new ArrayList<String>();
        List<Rows.EsEvent> events = null;
        Long head = null;
        List<Long> dupSeqs = null;
        Rows.Catalog catalog = null;
        try (var c = es()) {
            events = events(c, prefix);
            head = head(c);
            dupSeqs = duplicateSeqs(c);
            try {
                catalog = catalog(c);
            } catch (SQLException e) {
                errors.add("catalog: " + e.getMessage());
            }
        } catch (SQLException e) {
            errors.add("es db: " + e.getMessage());
        }
        Map<String, List<Rows.ViewRow>> regional = null;
        List<Rows.ViewRow> global = null;
        List<Rows.ObservedRow> observed = null;
        List<Rows.PaymentRow> payments = null;
        List<Rows.ConsumerStateRow> consumers = null;
        try (var c = app()) {
            regional = new LinkedHashMap<>();
            for (var r : LabConfig.REGIONS) {
                regional.put(r, views(c, LabConfig.regionTable(r), prefix));
            }
            global = views(c, "order_view_global", prefix);
            observed = observed(c, prefix);
            payments = payments(c, prefix);
            consumers = consumerStates(c);
        } catch (SQLException e) {
            errors.add("app db: " + e.getMessage());
        }
        return new Rows.Snapshot(prefix, events, head, dupSeqs, regional, global, observed, payments,
                consumers, catalog, errors);
    }

    public List<Rows.EsEvent> events(Connection c, String prefix) throws SQLException {
        var out = new ArrayList<Rows.EsEvent>();
        try (var ps = c.prepareStatement("""
                SELECT event_sequence_number, aggregate_id, event_name, context, event_message
                FROM es__events
                WHERE aggregate_id LIKE ? ESCAPE '\\' OR aggregate_id LIKE ? ESCAPE '\\'
                ORDER BY event_sequence_number""")) {
            ps.setString(1, likePrefix(prefix));
            ps.setString(2, likePrefix("payment-" + prefix));
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Rows.EsEvent(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5)));
                }
            }
        }
        return out;
    }

    public Long head(Connection c) throws SQLException {
        try (var st = c.createStatement(); var rs = st.executeQuery("SELECT max(event_sequence_number) FROM es__events")) {
            rs.next();
            long v = rs.getLong(1);
            return rs.wasNull() ? 0L : v;
        }
    }

    public long esHead() throws SQLException {
        try (var c = es()) {
            return head(c);
        }
    }

    /** Sequence numbers present more than once anywhere in the store (I3; impossible once G6's PK lands). */
    public List<Long> duplicateSeqs(Connection c) throws SQLException {
        var out = new ArrayList<Long>();
        try (var st = c.createStatement(); var rs = st.executeQuery("""
                SELECT event_sequence_number FROM es__events
                GROUP BY event_sequence_number HAVING count(*) > 1
                ORDER BY 1 LIMIT 100""")) {
            while (rs.next()) out.add(rs.getLong(1));
        }
        return out;
    }

    public List<Rows.ViewRow> views(Connection c, String table, String prefix) throws SQLException {
        if (!table.matches("[a-z_]+")) throw new IllegalArgumentException(table);
        var out = new ArrayList<Rows.ViewRow>();
        try (var ps = c.prepareStatement("""
                SELECT order_id, context, status, item_count, item_qty, payment_status, last_seq,
                       applied_events, stale_skips
                FROM %s WHERE order_id LIKE ? ESCAPE '\\' ORDER BY order_id""".formatted(table))) {
            ps.setString(1, likePrefix(prefix));
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Rows.ViewRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                            rs.getInt(5), rs.getString(6), rs.getLong(7), rs.getInt(8), rs.getInt(9)));
                }
            }
        } catch (SQLException e) {
            if ("42P01".equals(e.getSQLState())) return out; // table not created yet
            throw e;
        }
        return out;
    }

    public List<Rows.ObservedRow> observed(Connection c, String prefix) throws SQLException {
        var out = new ArrayList<Rows.ObservedRow>();
        try (var ps = c.prepareStatement(
                "SELECT event_seq, event_name, order_id, deliveries FROM observed_events WHERE order_id LIKE ? ESCAPE '\\'")) {
            ps.setString(1, likePrefix(prefix));
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Rows.ObservedRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4)));
                }
            }
        } catch (SQLException e) {
            if ("42P01".equals(e.getSQLState())) return out;
            throw e;
        }
        return out;
    }

    public List<Rows.PaymentRow> payments(Connection c, String prefix) throws SQLException {
        var out = new ArrayList<Rows.PaymentRow>();
        try (var ps = c.prepareStatement("SELECT order_id, attempts FROM payments WHERE order_id LIKE ? ESCAPE '\\'")) {
            ps.setString(1, likePrefix(prefix));
            try (var rs = ps.executeQuery()) {
                while (rs.next()) out.add(new Rows.PaymentRow(rs.getString(1), rs.getInt(2)));
            }
        } catch (SQLException e) {
            if ("42P01".equals(e.getSQLState())) return out;
            throw e;
        }
        return out;
    }

    public List<Rows.ConsumerStateRow> consumerStates(Connection c) throws SQLException {
        var out = new ArrayList<Rows.ConsumerStateRow>();
        try (var st = c.createStatement(); var rs = st.executeQuery(
                "SELECT consumer_id, kind, last_sequence, updated_at FROM evento_v2_consumer_state ORDER BY consumer_id")) {
            while (rs.next()) {
                out.add(new Rows.ConsumerStateRow(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4)));
            }
        } catch (SQLException e) {
            if ("42P01".equals(e.getSQLState())) return out;
            throw e;
        }
        return out;
    }

    public List<Rows.ConsumerStateRow> consumerStates() throws SQLException {
        try (var c = app()) {
            return consumerStates(c);
        }
    }

    public Rows.Catalog catalog(Connection c) throws SQLException {
        var bundles = new LinkedHashMap<String, String>();
        try (var st = c.createStatement(); var rs = st.executeQuery("SELECT id, instance_id FROM core__bundle ORDER BY id")) {
            while (rs.next()) bundles.put(rs.getString(1), rs.getString(2));
        }
        var handlers = new LinkedHashMap<String, java.util.Set<String>>();
        try (var st = c.createStatement(); var rs = st.executeQuery("""
                SELECT h.handled_payload_name, c.bundle_id
                FROM core__handler h JOIN core__component c ON h.component_component_name = c.component_name""")) {
            while (rs.next()) {
                handlers.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getString(2));
            }
        }
        var consumers = new LinkedHashMap<String, String>();
        try (var st = c.createStatement(); var rs = st.executeQuery(
                "SELECT consumer_id, instance_id FROM core__consumer ORDER BY consumer_id")) {
            while (rs.next()) consumers.put(rs.getString(1), rs.getString(2));
        }
        return new Rows.Catalog(bundles, handlers, consumers);
    }

    /**
     * Events whose serialized message contains {@code needle} (an idempotency
     * key: a UUID, so the substring match is exact enough). Used for orders
     * whose id the client never learned (gateway-generated ids, S14).
     */
    public List<Rows.EsEvent> eventsContaining(String needle) throws SQLException {
        var out = new ArrayList<Rows.EsEvent>();
        try (var c = es(); var ps = c.prepareStatement("""
                SELECT event_sequence_number, aggregate_id, event_name, context, event_message
                FROM es__events WHERE strpos(event_message, ?) > 0 ORDER BY event_sequence_number""")) {
            ps.setString(1, needle);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Rows.EsEvent(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5)));
                }
            }
        }
        return out;
    }

    /** Current value of each consumer's checkpoint, keyed by consumer id. */
    public Map<String, Long> checkpoints() throws SQLException {
        var out = new LinkedHashMap<String, Long>();
        for (var r : consumerStates()) out.put(r.consumerId(), r.lastSequence());
        return out;
    }
}
