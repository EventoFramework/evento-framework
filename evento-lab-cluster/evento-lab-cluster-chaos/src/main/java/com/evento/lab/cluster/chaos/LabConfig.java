package com.evento.lab.cluster.chaos;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Where the running lab is, as seen from the host. Every value can be
 * overridden by an environment variable (listed next to each default) so the
 * driver also works against a stack on another machine.
 */
public record LabConfig(
        Path composeFile,
        String lbUrl,
        String toxiproxyUrl,
        String esJdbcUrl,
        String appJdbcUrl,
        String dbUser,
        String dbPassword,
        Path resultsDir,
        String label,
        Map<String, Integer> serverHttpPorts) {

    public static final List<String> REGIONS = List.of(
            "EUROPA", "AFRICA", "AMERICA", "SUD_AMERICA", "CINA", "INDIA", "AUSTRALIA_NZ");

    public static final Map<String, String> CELL_OF = Map.of(
            "EUROPA", "EU", "AFRICA", "EU",
            "AMERICA", "AM", "SUD_AMERICA", "AM",
            "CINA", "APAC", "INDIA", "APAC", "AUSTRALIA_NZ", "APAC");

    public static final List<String> CELLS = List.of("EU", "AM", "APAC");

    /** Every bundle the default topology runs (profile 'replica' adds no new bundle id). */
    public static final List<String> EXPECTED_BUNDLES = List.of(
            "cluster-command", "cluster-query-global", "cluster-saga",
            "query-europa", "query-africa", "query-america", "query-sud_america",
            "query-cina", "query-india", "query-australia_nz");

    /** Payload types that must have a registered handler. */
    public static final List<String> EXPECTED_HANDLED_PAYLOADS = List.of(
            "CreateOrderCommand", "AddOrderItemCommand", "CompleteOrderCommand",
            "ProcessPaymentCommand", "FindGlobalOrderQuery");

    public static LabConfig fromEnvironment() {
        var composeFile = Path.of(firstNonBlank(
                System.getenv("EVENTO_LAB_COMPOSE_FILE"),
                System.getProperty("evento.lab.compose.file"),
                "../docker/compose.yaml")).toAbsolutePath().normalize();
        var results = Path.of(firstNonBlank(
                System.getenv("EVENTO_LAB_RESULTS_DIR"),
                System.getProperty("evento.lab.results.dir"),
                "../results")).toAbsolutePath().normalize();
        return new LabConfig(
                composeFile,
                firstNonBlank(System.getenv("EVENTO_LAB_LB_URL"), "http://localhost:8080"),
                firstNonBlank(System.getenv("EVENTO_LAB_TOXIPROXY_URL"), "http://localhost:8474"),
                firstNonBlank(System.getenv("EVENTO_LAB_ES_JDBC"),
                        "jdbc:postgresql://localhost:15432,localhost:15433,localhost:15434/evento"
                                + "?targetServerType=primary&connectTimeout=5&socketTimeout=60"),
                firstNonBlank(System.getenv("EVENTO_LAB_APP_JDBC"),
                        "jdbc:postgresql://localhost:25432,localhost:25433,localhost:25434/app"
                                + "?targetServerType=primary&connectTimeout=5&socketTimeout=60"),
                firstNonBlank(System.getenv("EVENTO_LAB_DB_USER"), "postgres"),
                firstNonBlank(System.getenv("EVENTO_LAB_DB_PASSWORD"), "secret"),
                results,
                firstNonBlank(System.getenv("EVENTO_LAB_LABEL"), "run"),
                Map.of("evento-eu", 13000, "evento-am", 13001, "evento-apac", 13002));
    }

    public static String regionService(String region) {
        return "region-" + region.toLowerCase().replace('_', '-');
    }

    public static String regionTable(String region) {
        return "order_view_" + region.toLowerCase();
    }

    public static String cellServer(String cell) {
        return "evento-" + cell.toLowerCase();
    }

    public static List<String> regionsOfCell(String cell) {
        return REGIONS.stream().filter(r -> CELL_OF.get(r).equals(cell)).toList();
    }

    /** Every app JVM of the default topology. */
    public static List<String> appServices() {
        var out = new java.util.ArrayList<String>(List.of("cell-core-eu", "cell-core-am", "cell-core-apac",
                "saga-eu", "saga-apac"));
        REGIONS.forEach(r -> out.add(regionService(r)));
        return out;
    }

    public static List<String> servers() {
        return List.of("evento-eu", "evento-am", "evento-apac");
    }

    public static int envInt(String name, int def) {
        var v = System.getenv(name);
        if (v == null || v.isBlank()) return def;
        return Integer.parseInt(v.trim());
    }

    public static boolean envBool(String name, boolean def) {
        var v = System.getenv(name);
        if (v == null || v.isBlank()) return def;
        return v.trim().equalsIgnoreCase("true") || v.trim().equals("1");
    }

    static String firstNonBlank(String... values) {
        for (var v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
