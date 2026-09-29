package com.evento.server.service.discovery;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.server.domain.model.core.Consumer;
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

    public boolean insertComponentIfAbsent(String componentName, ComponentType componentType, String bundleId,
                                           String description, String detail, String path, Integer line) {
        return jdbc.update("""
                insert into core__component (component_name, component_type, bundle_id, description, detail,
                                             path, line, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (component_name) do nothing
                """, ps -> {
            ps.setString(1, componentName);
            ps.setString(2, componentType == null ? null : componentType.name());
            ps.setString(3, bundleId);
            ps.setString(4, description);
            ps.setString(5, detail);
            ps.setString(6, path);
            if (line == null) ps.setNull(7, Types.INTEGER); else ps.setInt(7, line);
            ps.setTimestamp(8, now());
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
     * Upserts consumer rows. A row is keyed by {@code instanceId_consumerId}; a
     * re-registration (every reconnect re-sends it) refreshes the component link.
     */
    public void upsertConsumers(List<Consumer> consumers) {
        if (consumers.isEmpty()) return;
        jdbc.batchUpdate("""
                insert into core__consumer (identifier, component_component_name, instance_id, consumer_id)
                values (?, ?, ?, ?)
                on conflict (identifier) do update
                    set component_component_name = excluded.component_component_name,
                        instance_id = excluded.instance_id,
                        consumer_id = excluded.consumer_id
                """, consumers, consumers.size(), (ps, c) -> {
            ps.setString(1, c.getIdentifier());
            ps.setString(2, c.getComponent() == null ? null : c.getComponent().getComponentName());
            ps.setString(3, c.getInstanceId());
            ps.setString(4, c.getConsumerId());
        });
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }
}
