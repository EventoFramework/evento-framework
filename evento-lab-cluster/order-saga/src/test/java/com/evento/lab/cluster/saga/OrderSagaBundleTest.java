package com.evento.lab.cluster.saga;

import com.evento.lab.cluster.support.ContextSelection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderSagaBundleTest {

    @Test
    void oneSagaEnginePerRegionOfTheCell() {
        assertThat(OrderSagaBundle.sagaContexts(ContextSelection.parse("CINA,INDIA,AUSTRALIA_NZ")))
                .containsExactly("CINA", "INDIA", "AUSTRALIA_NZ");
        assertThat(OrderSagaBundle.sagaContexts(ContextSelection.parse("*"))).containsExactly("*");
        assertThat(OrderSagaBundle.sagaContexts(ContextSelection.parse("EUROPA,ALL"))).containsExactly("EUROPA");
    }

    @Test
    void theGlobalContextAloneIsRefused() {
        assertThatThrownBy(() -> OrderSagaBundle.sagaContexts(ContextSelection.parse("ALL")))
                .hasMessageContaining("no context");
    }
}
