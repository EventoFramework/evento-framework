package com.evento.lab.cluster;

import com.evento.lab.cluster.config.BundleSpec;
import com.evento.lab.cluster.config.LabSettings;
import com.evento.lab.cluster.config.Regions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Role/env parsing and the role-to-bundle mapping: no server, no database. */
class LabWiringTest {

    @Test
    void regionRoleDerivesCellBundleAndTable() {
        var s = LabSettings.from(Map.of(
                "EVENTO_ROLE", "region",
                "REGION", "sud_america",
                "EVENTO_SERVER_ADDRESSES", "evento-am:3030, toxi-am:20213,toxi-am:20233",
                "EVENTO_BUNDLE_INSTANCE_ID", "region-sud-america"));
        assertThat(s.role()).isEqualTo(LabSettings.Role.REGION);
        assertThat(s.region()).isEqualTo("SUD_AMERICA");
        assertThat(s.cell()).isEqualTo("AM");
        assertThat(s.serverAddresses()).extracting(Object::toString)
                .containsExactly("evento-am:3030", "toxi-am:20213", "toxi-am:20233");
        assertThat(s.checkpointMode()).isEqualTo("DEFAULT");
        assertThat(s.commandTimeoutMs()).isEqualTo(30_000);
        assertThat(s.expectedServerInstanceId()).isNull();

        var specs = BundleSpec.forSettings(s);
        assertThat(specs).hasSize(1);
        assertThat(specs.getFirst().bundleId()).isEqualTo("query-sud_america");
        assertThat(specs.getFirst().viewTable()).isEqualTo("order_view_sud_america");
        // one projector class per region (N1: the server keys components by simple name)
        assertThat(specs.getFirst().contexts()).containsEntry("SudAmericaOrderProjector", List.of("SUD_AMERICA"));
        assertThat(specs.getFirst().basePackage()).isEqualTo("com.evento.lab.cluster.bundle.region.sud_america");
        assertThat(s.bundleInstanceId("query-sud_america")).isEqualTo("region-sud-america-query-sud_america");
    }

    @Test
    void cellCoreHostsCommandAndGlobalQueryWithNoContext() {
        var s = LabSettings.from(Map.of("EVENTO_ROLE", "cell-core", "CELL", "apac",
                "CONSUMER_CHECKPOINT_MODE", "watermark", "EVENTO_EXPECTED_SERVER_INSTANCE_ID", "evento-apac"));
        assertThat(s.cell()).isEqualTo("APAC");
        assertThat(s.checkpointMode()).isEqualTo("WATERMARK");
        assertThat(s.expectedServerInstanceId()).isEqualTo("evento-apac");
        var specs = BundleSpec.forSettings(s);
        assertThat(specs).extracting(BundleSpec::bundleId).containsExactly("cluster-command", "cluster-query-global");
        assertThat(specs.get(1).contexts()).isEmpty();
        assertThat(specs.get(1).viewTable()).isEqualTo("order_view_global");
    }

    @Test
    void sagaObserverRole() {
        var s = LabSettings.from(Map.of("EVENTO_ROLE", "saga_observer", "CELL", "EU"));
        assertThat(BundleSpec.forSettings(s)).extracting(BundleSpec::bundleId).containsExactly("cluster-saga");
    }

    @Test
    void rejectsBadEnvironment() {
        assertThatThrownBy(() -> LabSettings.from(Map.of("EVENTO_ROLE", "region", "REGION", "ATLANTIS")))
                .hasMessageContaining("unknown region");
        assertThatThrownBy(() -> LabSettings.from(Map.of("EVENTO_ROLE", "cell-core")))
                .hasMessageContaining("cell");
        assertThatThrownBy(() -> LabSettings.from(Map.<String, String>of()))
                .hasMessageContaining("EVENTO_ROLE");
        assertThatThrownBy(() -> LabSettings.from(Map.of("EVENTO_ROLE", "saga-observer", "CELL", "EU",
                "CONSUMER_CHECKPOINT_MODE", "EVERY_EVENT"))).hasMessageContaining("CONSUMER_CHECKPOINT_MODE");
    }

    @Test
    void addressWithoutPortDefaultsToTheBusPort() {
        assertThat(LabSettings.parseAddresses("bus-eu")).extracting(Object::toString).containsExactly("bus-eu:3030");
    }

    @Test
    void everyRegionHasACellAndASafeTable() {
        for (var r : Regions.ALL) {
            assertThat(Regions.cellOf(r)).isIn(Regions.CELLS);
            assertThat(Regions.regionalTable(r)).matches("order_view_[a-z_]+");
        }
        assertThat(Regions.normalize("australia-nz")).isEqualTo("AUSTRALIA_NZ");
    }
}
