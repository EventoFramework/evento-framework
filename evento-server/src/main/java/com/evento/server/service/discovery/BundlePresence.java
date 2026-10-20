package com.evento.server.service.discovery;

import com.evento.server.bus.NodeAddress;
import com.evento.server.bus.spring.BusProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
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
 * <p>Rows carry a liveness timestamp that each server refreshes for its connected
 * instances ({@link #heartbeat}). A server that crashes and never comes back under the
 * same id stops refreshing; once its rows are older than {@code stale-after} they no
 * longer count as live sessions anywhere, so the bundles they kept alive can be
 * reclaimed. Timestamps come from the database clock, never a server's, so clock skew
 * between regions cannot age a row early.
 *
 * <p>Ownership requires a unique server instance id per server
 * ({@code evento.server.bus.server-instance-id}); with a shared id every server
 * believes it owns every row and the behaviour degrades to the single-server one.
 */
@Component
public class BundlePresence {

    private static final Logger logger = LoggerFactory.getLogger(BundlePresence.class);

    /** The id every server gets from the shipped application.properties (G13). */
    static final String SHIPPED_DEFAULT_SERVER_ID = "iris-evento-server";

    /** Outcome of {@link #release}: whether the leaving server may clean up after the instance. */
    public enum Release {
        /** This server owned the session; the row is gone and the cleanup is ours. */
        OWNED,
        /** No row: an instance that joined before this table existed. Legacy behaviour applies. */
        UNTRACKED,
        /** Another live server claimed the instance since; its registration must not be touched. */
        OWNED_ELSEWHERE
    }

    private final JdbcTemplate jdbc;
    private final String serverInstanceId;
    private final long staleAfterSeconds;

    @Autowired
    public BundlePresence(JdbcTemplate jdbc, BusProperties busProperties,
                          @Value("${evento.server.presence.stale-after-ms:180000}") long staleAfterMs,
                          @Value("${evento.server.presence.heartbeat-interval-ms:30000}") long heartbeatIntervalMs) {
        this(jdbc, busProperties.serverInstanceId(), Duration.ofMillis(staleAfterMs));
        checkTiming(Duration.ofMillis(staleAfterMs), Duration.ofMillis(heartbeatIntervalMs));
    }

    /**
     * Minimum ratio between the stale-after window and the heartbeat interval. Below it a single
     * late refresh (a slow database, a busy scheduler thread) lets live rows go stale, and then
     * {@link #liveElsewhere} and {@link #release} treat live sessions on other servers as dead,
     * which is the G7 failure the table exists to prevent.
     */
    static final int MIN_STALE_TO_HEARTBEAT_RATIO = 3;

    /**
     * Refuses a stale-after window no longer than the heartbeat interval (every row would go
     * stale between two refreshes) and warns below {@link #MIN_STALE_TO_HEARTBEAT_RATIO}.
     */
    static void checkTiming(Duration staleAfter, Duration heartbeatInterval) {
        if (heartbeatInterval.isNegative() || heartbeatInterval.isZero()) {
            throw new IllegalStateException("evento.server.presence.heartbeat-interval-ms must be positive");
        }
        if (staleAfter.compareTo(heartbeatInterval) <= 0) {
            throw new IllegalStateException(("evento.server.presence.stale-after-ms (%d) must be well above "
                    + "evento.server.presence.heartbeat-interval-ms (%d), at least %d times it: otherwise live "
                    + "presence rows go stale between refreshes and a server unregisters bundles that are "
                    + "still live on another server").formatted(staleAfter.toMillis(), heartbeatInterval.toMillis(),
                    MIN_STALE_TO_HEARTBEAT_RATIO));
        }
        if (staleAfter.compareTo(heartbeatInterval.multipliedBy(MIN_STALE_TO_HEARTBEAT_RATIO)) < 0) {
            logger.warn("event=bundle_presence_timing_tight staleAfterMs={} heartbeatIntervalMs={} "
                    + "hint=\"keep stale-after at least {} x heartbeat-interval, or one late refresh makes live "
                    + "instances look dead to other servers\"", staleAfter.toMillis(), heartbeatInterval.toMillis(),
                    MIN_STALE_TO_HEARTBEAT_RATIO);
        }
    }

    BundlePresence(JdbcTemplate jdbc, String serverInstanceId, Duration staleAfter) {
        this.jdbc = jdbc;
        this.serverInstanceId = serverInstanceId;
        this.staleAfterSeconds = Math.max(1, staleAfter.toSeconds());
    }

    public String serverInstanceId() {
        return serverInstanceId;
    }

    /**
     * Instances connected to any server of the cluster (a fresh presence row), and their distinct
     * bundles: {@code [instances, bundles]} (G17: the dashboard shows the cluster, not this server).
     */
    public long[] clusterCounts() {
        return jdbc.queryForObject("select count(*), count(distinct bundle_id) from core__bundle_instance "
                        + "where updated_at > now() - make_interval(secs => ?)",
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)}, staleAfterSeconds);
    }

    /**
     * A freshly started server holds no session at all, so any row still naming it is a
     * leftover from its previous life (a crash never ran the NodeLeft cleanup). Without
     * this purge those rows would count as live instances until they go stale.
     */
    @PostConstruct
    public void purgeOwnStaleRows() {
        if (SHIPPED_DEFAULT_SERVER_ID.equals(serverInstanceId)) {
            // Every server started from the shipped configuration shares this id: this
            // purge then deletes the live rows of all the others, and none of them can
            // tell its own sessions from another server's.
            logger.warn("event=bundle_presence_shared_server_id server_instance={} "
                    + "hint=\"set a unique evento.cluster.node.server.id per server when several servers share a catalog\"",
                    serverInstanceId);
        }
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
                values (?, ?, ?, now())
                on conflict (instance_id) do update
                    set bundle_id = excluded.bundle_id,
                        server_instance_id = excluded.server_instance_id,
                        updated_at = excluded.updated_at
                """, node.instanceId(), node.bundleId(), serverInstanceId);
    }

    /**
     * Refreshes the rows of the instances connected to this server, re-creating any that
     * went missing, and drops this server's own rows that no connected instance refreshes
     * anymore. A row another server holds is never taken over here: only a join
     * ({@link #claim}) moves ownership. Rows are written in instance-id order.
     */
    public void heartbeat(Collection<NodeAddress> connected) {
        var nodes = connected.stream().sorted(Comparator.comparing(NodeAddress::instanceId)).toList();
        if (!nodes.isEmpty()) {
            jdbc.batchUpdate("""
                    insert into core__bundle_instance (instance_id, bundle_id, server_instance_id, updated_at)
                    values (?, ?, ?, now())
                    on conflict (instance_id) do update
                        set updated_at = excluded.updated_at
                        where core__bundle_instance.server_instance_id = excluded.server_instance_id
                    """, nodes, nodes.size(), (ps, n) -> {
                ps.setString(1, n.instanceId());
                ps.setString(2, n.bundleId());
                ps.setString(3, serverInstanceId);
            });
        }
        jdbc.update("""
                delete from core__bundle_instance
                where server_instance_id = ? and updated_at < now() - make_interval(secs => ?)
                """, serverInstanceId, staleAfterSeconds);
    }

    /**
     * Drops this server's claim on {@code node}'s session. The delete is conditional on
     * ownership, so a concurrent claim by another server either lands first (and we see
     * {@link Release#OWNED_ELSEWHERE}) or waits on the row lock until the caller's
     * transaction commits the cleanup. A row held by a server that stopped heartbeating
     * is not a live session anywhere: the leaving server takes over its cleanup.
     */
    public Release release(NodeAddress node) {
        int deleted = jdbc.update("""
                delete from core__bundle_instance
                where instance_id = ?
                  and (server_instance_id = ? or updated_at < now() - make_interval(secs => ?))
                """, node.instanceId(), serverInstanceId, staleAfterSeconds);
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
     * True when another live server holds a session of some instance of {@code bundleId}.
     * This server's own sessions are not consulted here: the local connection view is
     * authoritative for them, and a stale row of ours must not keep a bundle alive. Rows
     * of a server that stopped heartbeating do not count either.
     */
    public boolean liveElsewhere(String bundleId, String exceptInstanceId) {
        Integer n = jdbc.queryForObject("""
                select count(*) from core__bundle_instance
                where bundle_id = ? and instance_id <> ? and server_instance_id <> ?
                  and updated_at >= now() - make_interval(secs => ?)
                """, Integer.class, bundleId, exceptInstanceId, serverInstanceId, staleAfterSeconds);
        return n != null && n > 0;
    }
}
