package com.evento.lab.cluster.support;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses {@code DATA_TARGETS}: the regional databases a fan-out consumer (the
 * global {@code ALL} lane) writes to, and that a gateway may read with
 * {@code ?db=<name>}.
 *
 * <p>Syntax: {@code name=jdbc:...} entries separated by {@code ;}. A comma
 * also separates entries, but only when it is followed by the next
 * {@code name=jdbc:}: a multi-host JDBC URL has commas of its own
 * ({@code jdbc:postgresql://a:5432,b:5432/app}), so a plain split on commas
 * would cut it apart. Names are lower-cased; order is kept.
 *
 * <pre>
 *   eu=jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary;am=jdbc:...;apac=jdbc:...
 * </pre>
 */
public final class DataTargets {

    private static final Pattern SEPARATOR = Pattern.compile("\\s*;\\s*|\\s*,\\s*(?=[A-Za-z0-9_-]+\\s*=\\s*jdbc:)");
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]+");

    private DataTargets() {
    }

    public static Map<String, String> parse(String raw) {
        var out = new LinkedHashMap<String, String>();
        if (raw == null || raw.isBlank()) return Collections.unmodifiableMap(out);
        for (var entry : SEPARATOR.split(raw.trim())) {
            if (entry.isBlank()) continue;
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("DATA_TARGETS entry '" + entry + "' is not name=jdbc:...");
            }
            var name = entry.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            var url = entry.substring(eq + 1).trim();
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("DATA_TARGETS: bad target name '" + name + "'");
            }
            if (!url.startsWith("jdbc:")) {
                throw new IllegalArgumentException("DATA_TARGETS: target " + name + " has no JDBC URL: '" + url + "'");
            }
            if (out.putIfAbsent(name, url) != null) {
                throw new IllegalArgumentException("DATA_TARGETS: target " + name + " listed twice");
            }
        }
        return Collections.unmodifiableMap(out);
    }
}
