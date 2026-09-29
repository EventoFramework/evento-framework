package com.evento.server.service.discovery;

import com.evento.server.bus.NodeAddress;
import com.evento.server.bus.spring.BusProperties;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Cluster-wide record of which evento-server holds the live session of each bundle
 * instance ({@code core__bundle_instance}).
 *
 * <p>Several servers share one catalog, but each server's connection view is in-JVM
 * only. When instance {@code I} fails over from server A to server B, A notices the
 * dead socket only after its heartbeat read-idle (45 s by default); by then B has
 * already re-registered {@code I}. A's {@code NodeLeft} used to clear {@code I}'s
 * consumer rows and unregister the whole bundle, because A's local view was the only
 * liveness signal it had. This row is the missing signal: B claims it on join, and A
 * acts on the leave only if it still owns the row.
 *
 * <p>Ownership requires a unique server instance id per server
 * ({@code evento.server.bus.server-instance-id}); with a shared id every server
 * believes it owns every row and the behaviour degrades to the single-server one.
 */
@Component
public class BundlePresence {

    private static final Logger logger = LogManager.getLogger(BundlePresence.class);

    /** Outcome of {@link #release}: whether the leaving server may clean up after the instance. */
    public enum Release {
        /** This server owned the session; the row is gone and the cleanup is ours. */
        OWNED,
        /** No row: an instance that joined before this table existed. Legacy behaviour applies. */
        UNTRACKED,
        /** Another server claimed the instance since; its registration must not be touched. */
        OWNED_ELSEWHERE
    }

    private final JdbcTemplate jdbc;
    private final String serverInstanceId;

    @Autowired
    public BundlePresence(JdbcTemplate jdbc, BusProperties busProperties) {
        this(jdbc, busProperties.serverInstanceId());
    }

    BundlePresence(JdbcTemplate jdbc, String serverInstanceId) {
        this.jdbc = jdbc;
        this.serverInstanceId = serverInstanceId;
    }

    public String serverInstanceId() {
        return serverInstanceId;
    }

    /**
     * A freshly started server holds no session at all, so any row still naming it is a
     * leftover from its previous life (a crash never ran the NodeLeft cleanup). Without
     * this purge those rows would count as live instances forever and keep dead bundles
     * in the catalog.
     */
    @PostConstruct
    public void purgeOwnStaleRows() {
        try {
            int purged = jdbc.update(
                    "delete from core__bundle_instance where server_instance_id = ?", serverInstanceId);
            if (purged > 0) {
                logger.info("event=bundle_presence_purged server_instance={} rows={}", serverInstanceId, purged);
            }
        } catch (RuntimeException e) {
            logger.warn("event=bundle_presence_purge_failed server_instance={}", serverInstanceId, e);
        }
    }

    /** Records that this server now holds the session of {@code node}, taking it over from any other server. */
    public void claim(NodeAddress node) {
        jdbc.update("""
                insert into core__bundle_instance (instance_id, bundle_id, server_instance_id, updated_at)
                values (?, ?, ?, ?)
                on conflict (instance_id) do update
                    set bundle_id = excluded.bundle_id,
                        server_instance_id = excluded.server_instance_id,
                        updated_at = excluded.updated_at
                """, node.instanceId(), node.bundleId(), serverInstanceId, Timestamp.from(Instant.now()));
    }

    /**
     * Drops this server's claim on {@code node}'s session. The delete is conditional on
     * ownership, so a concurrent claim by another server either lands first (and we see
     * {@link Release#OWNED_ELSEWHERE}) or waits on the row lock until the caller's
     * transaction commits the cleanup.
     */
    public Release release(NodeAddress node) {
        int deleted = jdbc.update(
                "delete from core__bundle_instance where instance_id = ? and server_instance_id = ?",
                node.instanceId(), serverInstanceId);
        if (deleted > 0) return Release.OWNED;
        return ownerOf(node.instanceId()).isPresent() ? Release.OWNED_ELSEWHERE : Release.UNTRACKED;
    }

    public Optional<String> ownerOf(String instanceId) {
        List<String> owners = jdbc.queryForList(
                "select server_instance_id from core__bundle_instance where instance_id = ?",
                String.class, instanceId);
        return owners.stream().findFirst();
    }

    /**
     * True when another server holds a session of some instance of {@code bundleId}.
     * This server's own sessions are not consulted here: the local connection view is
     * authoritative for them, and a stale row of ours must not keep a bundle alive.
     */
    public boolean liveElsewhere(String bundleId, String exceptInstanceId) {
        Integer n = jdbc.queryForObject("""
                select count(*) from core__bundle_instance
                where bundle_id = ? and instance_id <> ? and server_instance_id <> ?
                """, Integer.class, bundleId, exceptInstanceId, serverInstanceId);
        return n != null && n > 0;
    }
}
