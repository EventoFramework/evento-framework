package com.evento.test;

import com.evento.common.modeling.messaging.query.Single;
import com.evento.test.shop.Charge;
import com.evento.test.shop.FindPrice;
import com.evento.test.shop.ItemAdded;
import com.evento.test.shop.Notify;
import com.evento.test.shop.OrderCreated;
import com.evento.test.shop.OrderPaid;
import com.evento.test.shop.PaymentSaga;
import com.evento.test.shop.PaymentState;
import com.evento.test.shop.PriceView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SagaFixtureTest {

    private static SagaFixture<PaymentSaga> fixture() {
        var fixture = SagaFixture.of(new PaymentSaga());
        fixture.queryGateway().answer(FindPrice.class, q -> Single.of(new PriceView(10)));
        return fixture;
    }

    @Test
    void theInitializerAssociatesTheSaga() {
        fixture()
                .whenEvent(new OrderCreated("o1", 100))
                .expectAssociation("orderId", "o1")
                .expectNoCommands()
                .expectNotEnded();
    }

    @Test
    void theGivenEventsBuildTheStateAndTheirQueriesAreAnswered() {
        fixture()
                .given(new OrderCreated("o1", 100), new ItemAdded("o1", "pen", 0))
                .whenEvent(new ItemAdded("o1", "ink", 0))
                .expectState(PaymentState.class, s -> assertThat(s.total()).isEqualTo(20));
    }

    @Test
    void theCommandsSentByTheEventAreTheResult() {
        var fixture = fixture();
        fixture.given(new OrderCreated("o1", 100), new ItemAdded("o1", "pen", 0), new ItemAdded("o1", "ink", 0))
                .whenEvent(new OrderPaid("o1"))
                .expectCommands(new Charge("o1", 20), new Notify("o1"))
                .expectCommand(Charge.class, c -> assertThat(c.amount()).isEqualTo(20))
                .expectEnded();
        assertThat(fixture.queryGateway().asked()).hasSize(2);
    }

    @Test
    void aCommandThatFailsFailsTheHandler() {
        var fixture = fixture();
        fixture.commandGateway().answer(Charge.class, c -> {
            throw new IllegalStateException("card declined");
        });
        fixture.given(new OrderCreated("o1", 100))
                .whenEvent(new OrderPaid("o1"))
                .expectException(Exception.class);
    }

    @Test
    void aQueryWithoutAnAnswerIsAMistakeInTheTest() {
        assertThatThrownBy(() -> SagaFixture.of(new PaymentSaga())
                .given(new OrderCreated("o1", 100), new ItemAdded("o1", "pen", 0)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("failed on the given ItemAdded")
                .hasRootCauseMessage("no answer stubbed for " + FindPrice.class.getName() + "; add one with answer(...)");
    }

    @Test
    void differentCommandsFailWithBothLists() {
        var result = fixture()
                .given(new OrderCreated("o1", 100))
                .whenEvent(new OrderPaid("o1"));
        assertThatThrownBy(() -> result.expectCommands(new Charge("o1", 5)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("\"amount\":5")
                .hasMessageContaining("\"amount\":0")
                .hasMessageContaining("Notify");
    }
}
