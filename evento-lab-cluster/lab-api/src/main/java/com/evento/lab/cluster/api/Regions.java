package com.evento.lab.cluster.api;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The lab's geography: seven regions (= Evento contexts) grouped into three
 * cells (= one evento-server and one local app database each). Two region
 * names contain {@code _} on purpose: they are the regression target for the
 * LIKE-escaping gap (G15) and the consumerId parsing gap (G16).
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

    private Regions() {
    }

    /** {@code sud-america -> SUD_AMERICA}; throws for an unknown region. */
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

    public static boolean isRegion(String name) {
        return name != null && CELL_OF.containsKey(name);
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

    /** The regions of one cell, in {@link #ALL} order. */
    public static List<String> regionsOf(String cell) {
        var c = normalizeCell(cell);
        return ALL.stream().filter(r -> CELL_OF.get(r).equals(c)).toList();
    }
}
