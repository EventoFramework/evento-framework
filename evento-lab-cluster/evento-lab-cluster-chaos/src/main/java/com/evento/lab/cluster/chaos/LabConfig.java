package com.evento.lab.cluster.chaos;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Where the running lab is, as seen from the host, and what the default
 * topology deploys. Every location can be overridden by an environment
 * variable (listed next to each default) so the driver also works against a
 * stack on another machine.
 *
 * <p>The driver stays framework-free (it talks to the lab through docker,
 * HTTP and JDBC only), so the geography of the lab's {@code lab-api} is
 * restated here.
 *
 * @param appJdbcUrls cell -> JDBC URL of that cell's app database (primary + local replica)
 * @param consumersJdbcUrl the {@code consumers} database of the event-store cluster (global ALL lane state)
 */
public record LabConfig(
        Path composeFile,
        String lbUrl,
        String toxiproxyUrl,
        String esJdbcUrl,
        Map<String, String> appJdbcUrls,
        String consumersJdbcUrl,
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

    /** The literal cross-region context (lab-api {@code Contexts.GLOBAL}); NOT the wildcard {@code *}. */
    public static final String GLOBAL_CONTEXT = "ALL";

    /** The table of every regional order read model (one per cell database, told apart by context). */
    public static final String REGIONAL_TABLE = "order_view";
    public static final String GLOBAL_TABLE = "order_view_global";
    /** The cell whose database holds {@code order_view_global} (the wildcard deployment's). */
    public static final String GLOBAL_VIEW_CELL = "EU";

    /** Every bundle the default topology runs (profiles add no new bundle id). */
    public static final List<String> EXPECTED_BUNDLES = List.of(
            "order-command", "order-query", "order-saga", "order-observer", "order-gateway");

    /** Bundles that own no handler row by design (the gateway only sends). */
    public static final Set<String> BUNDLES_WITHOUT_HANDLERS = Set.of("order-gateway");

    /** Payload types that must have a registered handler. */
    public static final List<String> EXPECTED_HANDLED_PAYLOADS = List.of(
            "CreateOrderCommand", "AddOrderItemCommand", "CompleteOrderCommand", "ProcessPaymentCommand",
            "SetPriceCommand", "FindOrderQuery", "FindGlobalOrderQuery", "FindPriceQuery");

    /** The order-query deployment that consumes each region. */
    public static final Map<String, String> QUERY_SERVICE_OF = Map.of(
            "EUROPA", "order-query-eu", "AFRICA", "order-query-eu",
            "AMERICA", "order-query-am", "SUD_AMERICA", "order-query-am",
            "CINA", "order-query-cina",
            "INDIA", "order-query-apac", "AUSTRALIA_NZ", "order-query-apac");

    /** The two replicas of the single global ALL lane. */
    public static final List<String> GLOBAL_LANE_SERVICES = List.of("order-query-global-eu", "order-query-global-apac");
    public static final String WILDCARD_QUERY_SERVICE = "order-query-wildcard";
    public static final String OBSERVER_SERVICE = "order-observer";

    public static LabConfig fromEnvironment() {
        var composeFile = Path.of(firstNonBlank(
                System.getenv("EVENTO_LAB_COMPOSE_FILE"),
                System.getProperty("evento.lab.compose.file"),
                "../docker/compose.yaml")).toAbsolutePath().normalize();
        var results = Path.of(firstNonBlank(
                System.getenv("EVENTO_LAB_RESULTS_DIR"),
                System.getProperty("evento.lab.results.dir"),
                "../results")).toAbsolutePath().normalize();
        var opts = "?targetServerType=primary&connectTimeout=5&socketTimeout=60";
        var app = new LinkedHashMap<String, String>();
        app.put("EU", firstNonBlank(System.getenv("EVENTO_LAB_APP_JDBC_EU"),
                "jdbc:postgresql://localhost:25432,localhost:25442/app" + opts));
        app.put("AM", firstNonBlank(System.getenv("EVENTO_LAB_APP_JDBC_AM"),
                "jdbc:postgresql://localhost:25433,localhost:25443/app" + opts));
        app.put("APAC", firstNonBlank(System.getenv("EVENTO_LAB_APP_JDBC_APAC"),
                "jdbc:postgresql://localhost:25434,localhost:25444/app" + opts));
        return new LabConfig(
                composeFile,
                firstNonBlank(System.getenv("EVENTO_LAB_LB_URL"), "http://localhost:8080"),
                firstNonBlank(System.getenv("EVENTO_LAB_TOXIPROXY_URL"), "http://localhost:8474"),
                firstNonBlank(System.getenv("EVENTO_LAB_ES_JDBC"),
                        "jdbc:postgresql://localhost:15432,localhost:15433,localhost:15434/evento" + opts),
                Map.copyOf(app),
                firstNonBlank(System.getenv("EVENTO_LAB_CONSUMERS_JDBC"),
                        "jdbc:postgresql://localhost:15432,localhost:15433,localhost:15434/consumers" + opts),
                firstNonBlank(System.getenv("EVENTO_LAB_DB_USER"), "postgres"),
                firstNonBlank(System.getenv("EVENTO_LAB_DB_PASSWORD"), "secret"),
                results,
                firstNonBlank(System.getenv("EVENTO_LAB_LABEL"), "run"),
                Map.of("evento-eu", 13000, "evento-am", 13001, "evento-apac", 13002));
    }

    /** The gateway deployment of a region: {@code SUD_AMERICA -> gateway-sud-america}. */
    public static String regionService(String region) {
        return "gateway-" + region.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The order-query deployment consuming a region: {@code CINA -> order-query-cina}. */
    public static String queryService(String region) {
        return QUERY_SERVICE_OF.get(region);
    }

    public static String cellServer(String cell) {
        return "evento-" + cell.toLowerCase(Locale.ROOT);
    }

    /** The cell's app-database primary container: {@code APAC -> app-pg-apac}. */
    public static String appPg(String cell) {
        return "app-pg-" + cell.toLowerCase(Locale.ROOT);
    }

    public static List<String> regionsOfCell(String cell) {
        return REGIONS.stream().filter(r -> CELL_OF.get(r).equals(cell)).toList();
    }

    /** The cell whose database holds a context's regional rows, or null for a non-region context. */
    public static String cellOfContext(String context) {
        return context == null ? null : CELL_OF.get(context);
    }

    /** Every bundle JVM of one cell in the default topology. */
    public static List<String> cellJvms(String cell) {
        var c = cell.toLowerCase(Locale.ROOT);
        var out = new ArrayList<String>();
        out.add("order-command-" + c);
        switch (cell) {
            case "EU" -> out.addAll(List.of("order-query-eu", "order-query-global-eu", WILDCARD_QUERY_SERVICE,
                    OBSERVER_SERVICE));
            case "AM" -> out.add("order-query-am");
            case "APAC" -> out.addAll(List.of("order-query-cina", "order-query-apac", "order-query-global-apac"));
            default -> throw new IllegalArgumentException(cell);
        }
        out.add("order-saga-" + c);
        regionsOfCell(cell).forEach(r -> out.add(regionService(r)));
        return out;
    }

    /** Every bundle JVM of the default topology. */
    public static List<String> appServices() {
        var out = new ArrayList<String>();
        CELLS.forEach(c -> out.addAll(cellJvms(c)));
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
