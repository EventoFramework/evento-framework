package com.evento.lab.cluster.config;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Everything the lab app reads from its environment, parsed once. The parsing
 * is pure (it takes a lookup function) so it can be unit tested without
 * touching the real process environment.
 *
 * <p>Environment contract (see {@code evento-lab-cluster} README / compose):
 * <ul>
 *   <li>{@code EVENTO_ROLE} = {@code cell-core} | {@code region} | {@code saga-observer}</li>
 *   <li>{@code CELL} = EU | AM | APAC (cell-core, saga-observer; derived from REGION for region)</li>
 *   <li>{@code REGION} = one of {@link Regions#ALL} (region role)</li>
 *   <li>{@code EVENTO_SERVER_ADDRESSES} = {@code host:port[,host:port...]}</li>
 *   <li>{@code EVENTO_BUNDLE_INSTANCE_ID} (default: hostname)</li>
 *   <li>{@code EVENTO_EXPECTED_SERVER_INSTANCE_ID} optional wrong-broker guard (S13)</li>
 *   <li>{@code APP_DB_URL}, {@code APP_DB_USER}, {@code APP_DB_PASSWORD}, {@code APP_DB_POOL_SIZE}</li>
 *   <li>{@code CONSUMER_CHECKPOINT_MODE} = DEFAULT | WATERMARK (optional)</li>
 *   <li>{@code COMMAND_TIMEOUT_MS} (default 30000, the framework default)</li>
 * </ul>
 */
public record LabSettings(
        Role role,
        String cell,
        String region,
        List<Address> serverAddresses,
        String instanceId,
        String expectedServerInstanceId,
        String appDbUrl,
        String appDbUser,
        String appDbPassword,
        int appDbPoolSize,
        String checkpointMode,
        long commandTimeoutMs) {

    public enum Role {
        CELL_CORE("cell-core"), REGION("region"), SAGA_OBSERVER("saga-observer");

        private final String envValue;

        Role(String envValue) {
            this.envValue = envValue;
        }

        public String envValue() {
            return envValue;
        }

        public static Role parse(String v) {
            if (v == null || v.isBlank()) {
                throw new IllegalArgumentException("EVENTO_ROLE is required (cell-core | region | saga-observer)");
            }
            var n = v.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            for (var r : values()) {
                if (r.envValue.equals(n)) return r;
            }
            throw new IllegalArgumentException("unknown EVENTO_ROLE " + v);
        }
    }

    public record Address(String host, int port) {
        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    public static LabSettings fromEnvironment() {
        return from(System::getenv);
    }

    public static LabSettings from(Map<String, String> env) {
        return from(env::get);
    }

    public static LabSettings from(Function<String, String> env) {
        var role = Role.parse(env.apply("EVENTO_ROLE"));
        String region = null;
        String cell;
        if (role == Role.REGION) {
            region = Regions.normalize(env.apply("REGION"));
            var explicitCell = env.apply("CELL");
            cell = explicitCell == null || explicitCell.isBlank()
                    ? Regions.cellOf(region) : Regions.normalizeCell(explicitCell);
        } else {
            cell = Regions.normalizeCell(env.apply("CELL"));
        }
        var addresses = parseAddresses(orDefault(env.apply("EVENTO_SERVER_ADDRESSES"), "localhost:3030"));
        var instance = orDefault(env.apply("EVENTO_BUNDLE_INSTANCE_ID"), hostname());
        var expected = blankToNull(env.apply("EVENTO_EXPECTED_SERVER_INSTANCE_ID"));
        var url = orDefault(env.apply("APP_DB_URL"), "jdbc:postgresql://localhost:25432/app");
        var user = orDefault(env.apply("APP_DB_USER"), "postgres");
        var pass = orDefault(env.apply("APP_DB_PASSWORD"), "secret");
        var pool = Integer.parseInt(orDefault(env.apply("APP_DB_POOL_SIZE"), "12"));
        var mode = orDefault(env.apply("CONSUMER_CHECKPOINT_MODE"), "DEFAULT").trim().toUpperCase(Locale.ROOT);
        if (!mode.equals("DEFAULT") && !mode.equals("WATERMARK") && !mode.equals("ON_START")) {
            throw new IllegalArgumentException("CONSUMER_CHECKPOINT_MODE must be DEFAULT or WATERMARK, got " + mode);
        }
        var timeout = Long.parseLong(orDefault(env.apply("COMMAND_TIMEOUT_MS"), "30000"));
        return new LabSettings(role, cell, region, addresses, instance, expected, url, user, pass, pool, mode, timeout);
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

    /**
     * Instance id for one bundle of this JVM. Sibling bundles must not share an
     * instance id: the server clears consumer rows by instance on NodeLeft, so a
     * shared id would let one bundle's disconnect wipe its sibling's rows.
     */
    public String bundleInstanceId(String bundleId) {
        return instanceId + "-" + bundleId;
    }

    private static String hostname() {
        var h = System.getenv("HOSTNAME");
        if (h != null && !h.isBlank()) return h;
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }

    private static String orDefault(String v, String d) {
        return v == null || v.isBlank() ? d : v;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
