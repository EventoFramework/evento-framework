package com.evento.lab.cluster.config;

import java.util.List;
import java.util.Map;

/**
 * What one JVM must start: a pure description derived from {@link LabSettings}
 * so the role-to-bundle mapping is unit testable.
 *
 * @param bundleId     Evento bundle id
 * @param basePackage  package scanned for that bundle's components
 * @param contexts     component simple name -> contexts (empty map = all contexts)
 * @param viewTable    read-model table the bundle's projector writes, or {@code null}
 */
public record BundleSpec(String bundleId, String basePackage, Map<String, List<String>> contexts, String viewTable) {

    public static final String COMMAND_BUNDLE = "cluster-command";
    public static final String GLOBAL_QUERY_BUNDLE = "cluster-query-global";
    public static final String SAGA_BUNDLE = "cluster-saga";

    private static final String ROOT = "com.evento.lab.cluster.bundle.";

    public static List<BundleSpec> forSettings(LabSettings s) {
        return switch (s.role()) {
            case CELL_CORE -> List.of(
                    new BundleSpec(COMMAND_BUNDLE, ROOT + "command", Map.of(), null),
                    new BundleSpec(GLOBAL_QUERY_BUNDLE, ROOT + "global", Map.of(), Regions.GLOBAL_TABLE));
            case REGION -> List.of(
                    new BundleSpec(Regions.queryBundleId(s.region()), Regions.projectorPackage(s.region()),
                            Map.of(Regions.projectorName(s.region()), List.of(s.region())),
                            Regions.regionalTable(s.region())));
            case SAGA_OBSERVER -> List.of(
                    new BundleSpec(SAGA_BUNDLE, ROOT + "saga", Map.of(), null));
        };
    }
}
