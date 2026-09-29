package com.evento.server.service.discovery;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.server.domain.model.core.Consumer;
import com.evento.transport.protocol.PayloadDiscoveryInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;

/**
 * Race-free creation of the shared catalog rows ({@code core__bundle},
 * {@code core__component}, {@code core__payload}, {@code core__consumer}).
 *
 * <p>Discovery and consumer registration used to find-then-save through JPA. Two
 * servers (or two bus threads under different lock keys) that both found a row
 * missing both inserted it, and the loser aborted its whole pass with a
 * {@code duplicate key} error on the primary key, leaving the catalog half-written.
 * This is routine on a mass reconnect after a server dies, and component and
 * payload names are shared across bundles, so no per-bundle lock can cover them.
 * {@code INSERT ... ON CONFLICT DO NOTHING} makes the create idempotent at the
 * database: whoever comes second simply reads the row the first one wrote.
 *
 * <p>Each method returns whether this call created the row, so callers can keep
 * their "creating an ephemeral one" logging honest.
 */
@Component
public class CatalogWriter {

    private final JdbcTemplate jdbc;

    public CatalogWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean insertBundleIfAbsent(String id, long version, String description, String detail,
                                        String linePrefix, String repositoryUrl, String instanceId,
                                        boolean containsHandlers) {
        return jdbc.update("""
                insert into core__bundle (id, version, description, detail, line_prefix, repository_url,
                                          instance_id, contains_handlers, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do nothing
                """, id, version, description, detail, linePrefix, repositoryUrl, instanceId,
                containsHandlers, now()) > 0;
    }

    /**
     * Creates the component row of {@code bundleId}. Components are keyed per bundle
     * ({@link com.evento.server.domain.model.core.Component#idOf}), so the same class
     * deployed in another bundle gets a row of its own instead of colliding with this one.
     */
    public boolean insertComponentIfAbsent(String componentName, ComponentType componentType, String bundleId,
                                           String description, String detail, String path, Integer line) {
        return jdbc.update("""
                insert into core__component (id, component_name, component_type, bundle_id, description, detail,
                                             path, line, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do nothing
                """, ps -> {
            ps.setString(1, com.evento.server.domain.model.core.Component.idOf(bundleId, componentName));
            ps.setString(2, componentName);
            ps.setString(3, componentType == null ? null : componentType.name());
            ps.setString(4, bundleId);
            ps.setString(5, description);
            ps.setString(6, detail);
            ps.setString(7, path);
            if (line == null) ps.setNull(8, Types.INTEGER); else ps.setInt(8, line);
            ps.setTimestamp(9, now());
        }) > 0;
    }

    /** Creates a placeholder payload (no schema yet); the bundle's payload info backfills it. */
    public boolean insertPayloadIfAbsent(String name, PayloadType type, String registeredIn) {
        return jdbc.update("""
                insert into core__payload (name, type, registered_in, json_schema, is_valid_json_schema, updated_at)
                values (?, ?, ?, 'null', false, ?)
                on conflict (name) do nothing
                """, name, type == null ? null : type.name(), registeredIn, now()) > 0;
    }

    /**
     * Pins an existing payload row for the rest of the caller's transaction with
     * {@code FOR KEY SHARE}. That lock mode conflicts only with a delete (or a key
     * change), never with another discovery's key-share lock or metadata update, so it
     * cannot deadlock two discoveries; it only makes the orphan-payload sweep wait for
     * this transaction, after which the sweep's delete fails the foreign key of the
     * handler rows this pass wrote and the payload is kept.
     *
     * @return false when the row does not exist (the caller creates it)
     */
    public boolean lockPayload(String name) {
        return !jdbc.queryForList("select name from core__payload where name = ? for key share",
                String.class, name).isEmpty();
    }

    /**
     * Corrects the type of a shared payload row, writing only when it actually differs.
     * Meant to run outside any transaction, one row per statement, so it holds the row
     * lock for a single statement and cannot join a lock cycle with another discovery.
     */
    public void alignPayloadType(String name, PayloadType type) {
        var t = type == null ? null : type.name();
        jdbc.update("update core__payload set type = ?, updated_at = ? where name = ? and type is distinct from ?",
                t, now(), name, t);
    }

    /**
     * Backfills the metadata a bundle reports for one of its payloads. Empty values
     * never clobber stored ones, and the row is written only when something changes:
     * every bundle re-sends the same metadata on every reconnect, and an unconditional
     * write made every discovery lock every shared payload row it declares. Like
     * {@link #alignPayloadType}, it runs outside any transaction.
     */
    public void backfillPayloadInfo(String name, PayloadDiscoveryInfo info) {
        String schema = info.schema();
        String domain = info.domain();
        String description = emptyToNull(info.description());
        String detail = emptyToNull(info.detail());
        String path = emptyToNull(info.path());
        Integer line = info.line() > 0 ? info.line() : null;
        jdbc.update("""
                update core__payload set
                    json_schema = coalesce(?, json_schema),
                    domain = coalesce(?, domain),
                    description = coalesce(?, description),
                    detail = coalesce(?, detail),
                    path = coalesce(?, path),
                    line = coalesce(?, line),
                    is_valid_json_schema = true,
                    updated_at = ?
                where name = ?
                  and (json_schema is distinct from coalesce(?, json_schema)
                    or domain is distinct from coalesce(?, domain)
                    or description is distinct from coalesce(?, description)
                    or detail is distinct from coalesce(?, detail)
                    or path is distinct from coalesce(?, path)
                    or line is distinct from coalesce(?, line)
                    or not is_valid_json_schema)
                """, ps -> {
            int i = 0;
            ps.setString(++i, schema);
            ps.setString(++i, domain);
            ps.setString(++i, description);
            ps.setString(++i, detail);
            ps.setString(++i, path);
            setInt(ps, ++i, line);
            ps.setTimestamp(++i, now());
            ps.setString(++i, name);
            ps.setString(++i, schema);
            ps.setString(++i, domain);
            ps.setString(++i, description);
            ps.setString(++i, detail);
            ps.setString(++i, path);
            setInt(ps, ++i, line);
        });
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static void setInt(java.sql.PreparedStatement ps, int index, Integer value) throws java.sql.SQLException {
        if (value == null) ps.setNull(index, Types.INTEGER); else ps.setInt(index, value);
    }

    /**
     * Upserts consumer rows. A row is keyed by {@code instanceId_consumerId}, and the
     * consumer id carries the bundle, component, version and context, so instances of
     * one bundle running different contexts each keep their own rows. A
     * re-registration (every reconnect re-sends it) refreshes the component link.
     */
    public void upsertConsumers(List<Consumer> consumers) {
        if (consumers.isEmpty()) return;
        jdbc.batchUpdate("""
                insert into core__consumer (identifier, component_id, instance_id, consumer_id)
                values (?, ?, ?, ?)
                on conflict (identifier) do update
                    set component_id = excluded.component_id,
                        instance_id = excluded.instance_id,
                        consumer_id = excluded.consumer_id
                """, consumers, consumers.size(), (ps, c) -> {
            ps.setString(1, c.getIdentifier());
            ps.setString(2, c.getComponent() == null ? null : c.getComponent().getId());
            ps.setString(3, c.getInstanceId());
            ps.setString(4, c.getConsumerId());
        });
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }
}
