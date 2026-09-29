package com.evento.lab.cluster.support;

import com.evento.lab.cluster.api.Regions;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Everything a lab bundle reads from its environment, parsed once. The same
 * jar becomes a different deployment by changing only these values; nothing
 * about topology is compiled in. The parsing is pure (it takes a lookup
 * function) so it is unit tested without touching the process environment.
 *
 * <h2>Environment contract</h2>
 * <table>
 *   <caption>variables</caption>
 *   <tr><td>{@code EVENTO_BUNDLE_ID}</td><td>bundle id; defaults to the module's own ({@code order-query}, ...)</td></tr>
 *   <tr><td>{@code EVENTO_INSTANCE_ID}</td><td>instance id (fallback {@code EVENTO_BUNDLE_INSTANCE_ID}, then the hostname)</td></tr>
 *   <tr><td>{@code EVENTO_SERVER_ADDRESSES}</td><td>{@code host:port,...}, preferred broker first; all are passed to the bundle</td></tr>
 *   <tr><td>{@code EVENTO_CLUSTER_NAME}</td><td>the cluster the broker must announce (framework feature, see {@link FrameworkFeatures})</td></tr>
 *   <tr><td>{@code EVENTO_EXPECTED_SERVER_ID}</td><td>single-broker guard (2.4.6); fallback {@code EVENTO_EXPECTED_SERVER_INSTANCE_ID}</td></tr>
 *   <tr><td>{@code EVENTO_ZONE}</td><td>where the instance runs ({@code EU}/{@code AM}/{@code APAC}); advertised for locality routing</td></tr>
 *   <tr><td>{@code EVENTO_CONTEXTS}</td><td>consumed contexts, see {@link ContextSelection}</td></tr>
 *   <tr><td>{@code EVENTO_REGION}</td><td>the region a gateway serves (fallback {@code REGION})</td></tr>
 *   <tr><td>{@code EVENTO_CHECKPOINT_MODE}</td><td>{@code DEFAULT} | {@code WATERMARK} | {@code ON_START} (fallback {@code CONSUMER_CHECKPOINT_MODE})</td></tr>
 *   <tr><td>{@code APP_DB_*}</td><td>{@code _URL/_USER/_PASSWORD/_POOL_SIZE}: the cell's local app database (read models, side effects)</td></tr>
 *   <tr><td>{@code CONSUMER_STATE_DB_*}</td><td>where consumer state (checkpoints, locks, saga state) lives; defaults to {@code APP_DB_*}</td></tr>
 *   <tr><td>{@code GLOBAL_VIEW_DB_*}</td><td>where {@code order_view_global} lives; defaults to {@code APP_DB_*}</td></tr>
 *   <tr><td>{@code DATA_TARGETS}</td><td>fan-out targets {@code name=jdbc:...;...}, see {@link DataTargets}</td></tr>
 *   <tr><td>{@code COMMAND_TIMEOUT_MS}</td><td>gateway command/query timeout (30000)</td></tr>
 *   <tr><td>{@code EVENTO_HEARTBEAT_READ_IDLE_S}</td><td>silent-partition detection (framework feature)</td></tr>
 * </table>
 */
public record BundleDeployment(
        String bundleId,
        String instanceId,
        List<Address> serverAddresses,
        String clusterName,
        String expectedServerId,
        String zone,
        String cell,
        String region,
        ContextSelection contexts,
        String checkpointMode,
        DbSettings appDb,
        DbSettings consumerStateDb,
        DbSettings globalViewDb,
        Map<String, String> dataTargets,
        long commandTimeoutMs,
        Duration heartbeatReadIdle) {

    public record Address(String host, int port) {
        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    public static BundleDeployment fromEnvironment(String defaultBundleId) {
        return from(System::getenv, defaultBundleId);
    }

    public static BundleDeployment from(Map<String, String> env, String defaultBundleId) {
        return from(env::get, defaultBundleId);
    }

    public static BundleDeployment from(Function<String, String> env, String defaultBundleId) {
        var bundleId = orDefault(env.apply("EVENTO_BUNDLE_ID"), defaultBundleId);
        if (bundleId == null || bundleId.isBlank()) {
            throw new IllegalArgumentException("EVENTO_BUNDLE_ID is required");
        }
        var instance = firstNonBlank(env.apply("EVENTO_INSTANCE_ID"), env.apply("EVENTO_BUNDLE_INSTANCE_ID"),
                env.apply("HOSTNAME"), hostname());
        var addresses = parseAddresses(orDefault(env.apply("EVENTO_SERVER_ADDRESSES"), "localhost:3030"));
        var clusterName = blankToNull(env.apply("EVENTO_CLUSTER_NAME"));
        var expected = blankToNull(firstNonBlank(env.apply("EVENTO_EXPECTED_SERVER_ID"),
                env.apply("EVENTO_EXPECTED_SERVER_INSTANCE_ID")));
        var zone = blankToNull(env.apply("EVENTO_ZONE"));
        if (zone != null) zone = zone.trim().toUpperCase(Locale.ROOT);
        var cellRaw = firstNonBlank(env.apply("CELL"), zone != null && Regions.CELLS.contains(zone) ? zone : null);
        var region = blankToNull(firstNonBlank(env.apply("EVENTO_REGION"), env.apply("REGION")));
        if (region != null) region = Regions.normalize(region);
        String cell = cellRaw == null ? (region == null ? null : Regions.cellOf(region)) : Regions.normalizeCell(cellRaw);
        var rawContexts = env.apply("EVENTO_CONTEXTS");
        var contexts = rawContexts == null || rawContexts.isBlank() ? null : ContextSelection.parse(rawContexts);
        var mode = orDefault(firstNonBlank(env.apply("EVENTO_CHECKPOINT_MODE"), env.apply("CONSUMER_CHECKPOINT_MODE")),
                "DEFAULT").trim().toUpperCase(Locale.ROOT);
        if (!mode.equals("DEFAULT") && !mode.equals("WATERMARK") && !mode.equals("ON_START")) {
            throw new IllegalArgumentException("EVENTO_CHECKPOINT_MODE must be DEFAULT, WATERMARK or ON_START, got " + mode);
        }
        var appDb = DbSettings.from(env, "APP_DB", null, 12);
        var consumerDb = DbSettings.from(env, "CONSUMER_STATE_DB", appDb, 12);
        var globalViewDb = DbSettings.from(env, "GLOBAL_VIEW_DB", appDb, 4);
        var targets = DataTargets.parse(env.apply("DATA_TARGETS"));
        var timeout = Long.parseLong(orDefault(env.apply("COMMAND_TIMEOUT_MS"), "30000"));
        var readIdleRaw = blankToNull(env.apply("EVENTO_HEARTBEAT_READ_IDLE_S"));
        var readIdle = readIdleRaw == null ? null : Duration.ofSeconds(Long.parseLong(readIdleRaw));
        return new BundleDeployment(bundleId.trim(), instance, addresses, clusterName, expected, zone, cell, region,
                contexts, mode, appDb, consumerDb, globalViewDb, targets, timeout, readIdle);
    }

    /** {@code "a:3030, b:3031,c"} -> [a:3030, b:3031, c:3030]. */
    public static List<Address> parseAddresses(String raw) {
        var out = new ArrayList<Address>();
        for (var part : raw.split(",")) {
            var p = part.trim();
            if (p.isEmpty()) continue;
            int idx = p.lastIndexOf(':');
            if (idx < 0) {
                out.add(new Address(p, 3030));
            } else {
                out.add(new Address(p.substring(0, idx), Integer.parseInt(p.substring(idx + 1).trim())));
            }
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("EVENTO_SERVER_ADDRESSES has no address: '" + raw + "'");
        }
        return List.copyOf(out);
    }

    /** The contexts, or a startup failure naming the variable: consumer bundles must say what they consume. */
    public ContextSelection requireContexts() {
        if (contexts == null) {
            throw new IllegalStateException("EVENTO_CONTEXTS is required for bundle " + bundleId
                    + ": list regions (EUROPA,AFRICA), ALL (the literal global context) or * (every context)");
        }
        return contexts;
    }

    public DbSettings requireAppDb() {
        if (appDb == null) {
            throw new IllegalStateException("APP_DB_URL is required for bundle " + bundleId);
        }
        return appDb;
    }

    public String requireRegion() {
        if (region == null) {
            throw new IllegalStateException("EVENTO_REGION is required for bundle " + bundleId);
        }
        return region;
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }

    static String orDefault(String v, String d) {
        return v == null || v.isBlank() ? d : v;
    }

    static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    static String firstNonBlank(String... values) {
        for (var v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }
}
