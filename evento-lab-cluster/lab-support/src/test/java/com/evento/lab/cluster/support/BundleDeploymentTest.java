package com.evento.lab.cluster.support;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The deployment contract: environment in, deployment out. No server, no database. */
class BundleDeploymentTest {

    @Test
    void regionalQueryDeploymentInTheEuCell() {
        var d = BundleDeployment.from(Map.of(
                "EVENTO_INSTANCE_ID", "order-query-eu",
                "EVENTO_SERVER_ADDRESSES", "evento-eu:3030, toxi-eu:20123,toxi-eu:20133",
                "EVENTO_CLUSTER_NAME", "evento-lab-cluster",
                "EVENTO_ZONE", "eu",
                "EVENTO_CONTEXTS", "europa, AFRICA",
                "APP_DB_URL", "jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary"),
                "order-query");
        assertThat(d.bundleId()).isEqualTo("order-query");
        assertThat(d.instanceId()).isEqualTo("order-query-eu");
        assertThat(d.serverAddresses()).extracting(Object::toString)
                .containsExactly("evento-eu:3030", "toxi-eu:20123", "toxi-eu:20133");
        assertThat(d.zone()).isEqualTo("EU");
        assertThat(d.cell()).isEqualTo("EU");
        assertThat(d.clusterName()).isEqualTo("evento-lab-cluster");
        assertThat(d.expectedServerId()).isNull();
        assertThat(d.contexts().contexts()).containsExactly("EUROPA", "AFRICA");
        assertThat(d.checkpointMode()).isEqualTo("DEFAULT");
        // consumer state and the global view default to the cell's own database
        assertThat(d.consumerStateDb()).isEqualTo(d.appDb());
        assertThat(d.globalViewDb()).isEqualTo(d.appDb());
        assertThat(d.appDb().user()).isEqualTo("postgres");
        assertThat(d.dataTargets()).isEmpty();
        assertThat(d.commandTimeoutMs()).isEqualTo(30_000);
        assertThat(d.heartbeatReadIdle()).isNull();
    }

    @Test
    void globalLaneKeepsItsStateInTheSharedConsumersDatabase() {
        var d = BundleDeployment.from(Map.of(
                "EVENTO_BUNDLE_ID", "order-query",
                "EVENTO_ZONE", "APAC",
                "EVENTO_CONTEXTS", "ALL",
                "APP_DB_URL", "jdbc:postgresql://app-pg-apac:5432/app",
                "CONSUMER_STATE_DB_URL", "jdbc:postgresql://toxi-apac:20311,es-pg-apac:5432/consumers?targetServerType=primary",
                "CONSUMER_STATE_DB_POOL_SIZE", "6",
                "DATA_TARGETS", "eu=jdbc:postgresql://toxi-apac:20312/app;am=jdbc:postgresql://toxi-apac:20322/app,"
                        + "apac=jdbc:postgresql://app-pg-apac:5432,app-pg-apac-r:5432/app?targetServerType=primary",
                "EVENTO_HEARTBEAT_READ_IDLE_S", "10"), "x");
        assertThat(d.contexts().includesGlobal()).isTrue();
        assertThat(d.contexts().isWildcard()).isFalse();
        assertThat(d.consumerStateDb().url()).endsWith("/consumers?targetServerType=primary");
        assertThat(d.consumerStateDb().sameAs(d.appDb())).isFalse();
        assertThat(d.consumerStateDb().poolSize()).isEqualTo(6);
        assertThat(d.dataTargets()).containsOnlyKeys("eu", "am", "apac");
        assertThat(d.dataTargets().get("apac")).contains("app-pg-apac:5432,app-pg-apac-r:5432");
        assertThat(d.heartbeatReadIdle()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void gatewayDerivesItsCellFromItsRegion() {
        var d = BundleDeployment.from(Map.of("EVENTO_REGION", "sud-america", "EVENTO_BUNDLE_INSTANCE_ID", "gw"),
                "order-gateway");
        assertThat(d.region()).isEqualTo("SUD_AMERICA");
        assertThat(d.cell()).isEqualTo("AM");
        assertThat(d.instanceId()).isEqualTo("gw");
        assertThat(d.contexts()).isNull();
        assertThat(d.appDb()).isNull();
        assertThatThrownBy(d::requireContexts).hasMessageContaining("EVENTO_CONTEXTS is required");
        assertThatThrownBy(d::requireAppDb).hasMessageContaining("APP_DB_URL");
    }

    @Test
    void legacyNamesStillWork() {
        var d = BundleDeployment.from(Map.of("CONSUMER_CHECKPOINT_MODE", "watermark",
                "EVENTO_EXPECTED_SERVER_INSTANCE_ID", "evento-eu"), "b");
        assertThat(d.checkpointMode()).isEqualTo("WATERMARK");
        assertThat(d.expectedServerId()).isEqualTo("evento-eu");
        var explicit = BundleDeployment.from(Map.of("EVENTO_CHECKPOINT_MODE", "on_start",
                "CONSUMER_CHECKPOINT_MODE", "WATERMARK", "EVENTO_EXPECTED_SERVER_ID", "evento-am"), "b");
        assertThat(explicit.checkpointMode()).isEqualTo("ON_START");
        assertThat(explicit.expectedServerId()).isEqualTo("evento-am");
    }

    @Test
    void rejectsBadEnvironment() {
        assertThatThrownBy(() -> BundleDeployment.from(Map.of("EVENTO_CHECKPOINT_MODE", "EVERY_EVENT"), "b"))
                .hasMessageContaining("EVENTO_CHECKPOINT_MODE");
        assertThatThrownBy(() -> BundleDeployment.from(Map.of("EVENTO_REGION", "ATLANTIS"), "b"))
                .hasMessageContaining("unknown region");
        assertThatThrownBy(() -> BundleDeployment.from(Map.of("CELL", "MARS"), "b"))
                .hasMessageContaining("unknown cell");
        assertThatThrownBy(() -> BundleDeployment.from(Map.of(), null))
                .hasMessageContaining("EVENTO_BUNDLE_ID");
        assertThatThrownBy(() -> BundleDeployment.from(Map.of("EVENTO_CONTEXTS", "EUROPE"), "b"))
                .hasMessageContaining("unknown context 'EUROPE'");
    }

    @Test
    void addressWithoutPortDefaultsToTheBusPort() {
        assertThat(BundleDeployment.parseAddresses("bus-eu")).extracting(Object::toString).containsExactly("bus-eu:3030");
        assertThatThrownBy(() -> BundleDeployment.parseAddresses(" , ")).hasMessageContaining("no address");
    }

    @Test
    void aNonCellZoneLeavesTheCellUnset() {
        var d = BundleDeployment.from(Map.of("EVENTO_ZONE", "lab-rack-7"), "b");
        assertThat(d.zone()).isEqualTo("LAB-RACK-7");
        assertThat(d.cell()).isNull();
        assertThat(List.of(d.serverAddresses().getFirst().toString())).containsExactly("localhost:3030");
    }
}
