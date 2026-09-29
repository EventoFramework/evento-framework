package com.evento.lab.cluster.query;

import com.evento.lab.cluster.support.ContextSelection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** EVENTO_CONTEXTS to consumer engines, for the three kinds of order-query deployment. */
class OrderQueryBundleTest {

    @Test
    void regionalDeploymentRunsOneOrderEnginePerRegionAndNoGlobalLane() {
        var s = ContextSelection.parse("EUROPA,AFRICA");
        assertThat(OrderQueryBundle.plan(s))
                .containsEntry("OrderProjector", List.of("EUROPA", "AFRICA"))
                .containsEntry("CatalogProjector", List.of());
        assertThat(OrderQueryBundle.writerTable(s)).isEqualTo("order_view");
    }

    @Test
    void globalDeploymentRunsOnlyTheGlobalLane() {
        var s = ContextSelection.parse("ALL");
        assertThat(OrderQueryBundle.plan(s))
                .containsEntry("OrderProjector", List.of())
                .containsEntry("CatalogProjector", List.of("ALL"));
    }

    @Test
    void wildcardDeploymentFeedsTheGlobalViewButNeverOpensASecondGlobalLane() {
        var s = ContextSelection.parse("*");
        assertThat(OrderQueryBundle.plan(s))
                .containsEntry("OrderProjector", List.of("*"))
                .containsEntry("CatalogProjector", List.of());
        assertThat(OrderQueryBundle.writerTable(s)).isEqualTo("order_view_global");
    }

    @Test
    void aSingleRegionDeploymentAndAMixedOne() {
        assertThat(OrderQueryBundle.plan(ContextSelection.parse("CINA")))
                .containsEntry("OrderProjector", List.of("CINA"));
        var mixed = ContextSelection.parse("INDIA,ALL");
        assertThat(OrderQueryBundle.plan(mixed))
                .containsEntry("OrderProjector", List.of("INDIA"))
                .containsEntry("CatalogProjector", List.of("ALL"));
        assertThat(OrderQueryBundle.writerTable(mixed)).isEqualTo("order_view");
    }
}
