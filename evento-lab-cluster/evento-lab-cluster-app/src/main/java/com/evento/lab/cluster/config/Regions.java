package com.evento.lab.cluster.config;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The lab's geography: seven regions (= Evento contexts) grouped into three
 * cells (= one evento-server each). Two region names contain {@code _} on
 * purpose, they are the regression target for the LIKE-escaping gap (G15) and
 * the consumerId parsing gap (G16).
 */
public final class Regions {

    public static final List<String> ALL = List.of(
            "EUROPA", "AFRICA", "AMERICA", "SUD_AMERICA", "CINA", "INDIA", "AUSTRALIA_NZ");

    public static final Map<String, String> CELL_OF = Map.of(
            "EUROPA", "EU",
            "AFRICA", "EU",
            "AMERICA", "AM",
            "SUD_AMERICA", "AM",
            "CINA", "APAC",
            "INDIA", "APAC",
            "AUSTRALIA_NZ", "APAC");

    public static final List<String> CELLS = List.of("EU", "AM", "APAC");

    private static final Pattern SQL_IDENT_SUFFIX = Pattern.compile("[a-z_]+");

    private Regions() {
    }

    public static String normalize(String region) {
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("region is required");
        }
        var r = region.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!CELL_OF.containsKey(r)) {
            throw new IllegalArgumentException("unknown region " + region + ", expected one of " + ALL);
        }
        return r;
    }

    public static String cellOf(String region) {
        return CELL_OF.get(normalize(region));
    }

    public static String normalizeCell(String cell) {
        if (cell == null || cell.isBlank()) {
            throw new IllegalArgumentException("cell is required");
        }
        var c = cell.trim().toUpperCase(Locale.ROOT);
        if (!CELLS.contains(c)) {
            throw new IllegalArgumentException("unknown cell " + cell + ", expected one of " + CELLS);
        }
        return c;
    }

    /** {@code EUROPA -> europa}, {@code SUD_AMERICA -> sud_america}. */
    public static String lower(String region) {
        return normalize(region).toLowerCase(Locale.ROOT);
    }

    /** Bundle id of a region's query bundle: {@code query-sud_america}. */
    public static String queryBundleId(String region) {
        return "query-" + lower(region);
    }

    /**
     * Simple class name of a region's projector: {@code SUD_AMERICA -> SudAmericaOrderProjector}.
     * One class per region because the server keys components by simple name (N1).
     */
    public static String projectorName(String region) {
        var sb = new StringBuilder();
        for (var part : lower(region).split("_")) {
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.append("OrderProjector").toString();
    }

    /** Package holding only that region's projector: {@code ...bundle.region.sud_america}. */
    public static String projectorPackage(String region) {
        return "com.evento.lab.cluster.bundle.region." + lower(region);
    }

    /** Read-model table of a region: {@code order_view_sud_america}. */
    public static String regionalTable(String region) {
        return safeTable("order_view_" + lower(region));
    }

    public static final String GLOBAL_TABLE = "order_view_global";

    static String safeTable(String name) {
        if (!SQL_IDENT_SUFFIX.matcher(name).matches()) {
            throw new IllegalArgumentException("unsafe table name " + name);
        }
        return name;
    }
}
