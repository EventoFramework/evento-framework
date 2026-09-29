package com.evento.lab.cluster.support.store;

import com.evento.lab.cluster.api.view.PriceViewDto;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * {@code catalog_price} in one regional database, as a fan-out
 * {@link PriceTarget} and as the read side of {@code FindPriceQuery}.
 *
 * <p>The write is one statement: an upsert that applies only when the event's
 * sequence number is above the stored {@code last_seq}. A replay therefore
 * changes nothing but the {@code stale_skips} counter, whatever order the
 * targets are retried in. The table is created on first use, so a target that
 * was down when the fan-out JVM booted is initialised when it comes back.
 */
public class CatalogStore implements PriceTarget {

    private static final String UPSERT = """
            INSERT INTO catalog_price AS t (sku, price_cents, currency, context, last_seq, applied_events, updated_by)
            VALUES (?, ?, ?, ?, ?, 1, ?)
            ON CONFLICT (sku) DO UPDATE SET
                price_cents = EXCLUDED.price_cents,
                currency = EXCLUDED.currency,
                context = EXCLUDED.context,
                last_seq = EXCLUDED.last_seq,
                applied_events = t.applied_events + 1,
                updated_by = EXCLUDED.updated_by,
                updated_at = now()
            WHERE t.last_seq < EXCLUDED.last_seq""";

    private static final String SKIP = "UPDATE catalog_price SET stale_skips = stale_skips + 1 WHERE sku = ?";

    private static final String FIND = """
            SELECT sku, price_cents, currency, context, last_seq, applied_events, stale_skips, updated_by
            FROM catalog_price WHERE sku = ?""";

    private final String name;
    private final DataSource dataSource;
    private final String writer;
    private volatile boolean schemaReady;

    public CatalogStore(String name, DataSource dataSource, String writer) {
        this.name = name;
        this.dataSource = dataSource;
        this.writer = writer;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Outcome apply(PriceChange change) throws SQLException {
        ensureSchema();
        try (var con = dataSource.getConnection()) {
            int updated;
            try (var ps = con.prepareStatement(UPSERT)) {
                ps.setString(1, change.sku());
                ps.setLong(2, change.priceCents());
                ps.setString(3, change.currency());
                ps.setString(4, change.context());
                ps.setLong(5, change.seq());
                ps.setString(6, writer);
                updated = ps.executeUpdate();
            }
            if (updated > 0) return Outcome.APPLIED;
            try (var ps = con.prepareStatement(SKIP)) {
                ps.setString(1, change.sku());
                ps.executeUpdate();
            }
            return Outcome.STALE;
        }
    }

    public Optional<PriceViewDto> find(String sku) throws SQLException {
        ensureSchema();
        try (var con = dataSource.getConnection(); var ps = con.prepareStatement(FIND)) {
            ps.setString(1, sku);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                var v = new PriceViewDto();
                v.setSku(rs.getString("sku"));
                v.setPriceCents(rs.getLong("price_cents"));
                v.setCurrency(rs.getString("currency"));
                v.setContext(rs.getString("context"));
                v.setLastSeq(rs.getLong("last_seq"));
                v.setAppliedEvents(rs.getInt("applied_events"));
                v.setStaleSkips(rs.getInt("stale_skips"));
                v.setUpdatedBy(rs.getString("updated_by"));
                v.setDatabase(name);
                return Optional.of(v);
            }
        }
    }

    private void ensureSchema() throws SQLException {
        if (schemaReady) return;
        LabSchema.initialise(dataSource, false, List.of(LabSchema.catalogDdl()));
        schemaReady = true;
    }
}
