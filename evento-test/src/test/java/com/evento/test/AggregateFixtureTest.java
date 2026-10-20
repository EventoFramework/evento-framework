package com.evento.test;

import com.evento.common.modeling.exceptions.AggregateInitializedError;
import com.evento.common.modeling.exceptions.AggregateNotInitializedError;
import com.evento.test.shop.AddItem;
import com.evento.test.shop.AddPair;
import com.evento.test.shop.DeleteThenAdd;
import com.evento.test.shop.CreateOrder;
import com.evento.test.shop.DeleteOrder;
import com.evento.test.shop.ItemAdded;
import com.evento.test.shop.OrderAggregate;
import com.evento.test.shop.OrderCreated;
import com.evento.test.shop.OrderState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregateFixtureTest {

    @Test
    void anInitializerProducesItsEventFromNoHistory() {
        AggregateFixture.of(new OrderAggregate())
                .givenNoPriorActivity()
                .when(new CreateOrder("o1", 100))
                .expectEvent(new OrderCreated("o1", 100));
    }

    @Test
    void theHistoryRebuildsTheStateTheCommandDecidesOn() {
        AggregateFixture.of(new OrderAggregate())
                .given(new OrderCreated("o1", 100), new ItemAdded("o1", "pen", 60))
                .when(new AddItem("o1", "ink", 30))
                .expectEvent(new ItemAdded("o1", "ink", 30))
                .expectState(OrderState.class, s -> {
                    assertThat(s.items()).containsExactly("pen", "ink");
                    assertThat(s.spent()).isEqualTo(90);
                });
    }

    @Test
    void aRuleTheCommandBreaksIsTheException() {
        AggregateFixture.of(new OrderAggregate())
                .given(new OrderCreated("o1", 100), new ItemAdded("o1", "pen", 60))
                .when(new AddItem("o1", "book", 50))
                .expectException(IllegalStateException.class, e -> assertThat(e).hasMessage("over budget"));
    }

    @Test
    void theFrameworkRulesOnInitializersApply() {
        AggregateFixture.of(new OrderAggregate())
                .when(new AddItem("o1", "pen", 1))
                .expectException(AggregateNotInitializedError.class);
        AggregateFixture.of(new OrderAggregate())
                .given(new OrderCreated("o1", 100))
                .when(new CreateOrder("o1", 100))
                .expectException(AggregateInitializedError.class);
    }

    @Test
    void severalEventsAreAppliedInOrder() {
        var result = AggregateFixture.of(new OrderAggregate())
                .given(new OrderCreated("o1", 100))
                .when(new AddPair("o1", "pen", "ink", 20))
                .expectEvents(new ItemAdded("o1", "pen", 20), new ItemAdded("o1", "ink", 20))
                .expectState(OrderState.class, s -> {
                    assertThat(s.items()).containsExactly("pen", "ink");
                    assertThat(s.spent()).isEqualTo(40);
                });
        assertThat(result.event()).as("the caller's answer: the last event").isEqualTo(result.events().getLast());
        assertThatThrownBy(() -> result.expectEvent(new ItemAdded("o1", "ink", 20)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("2 events");
        assertThatThrownBy(() -> result.expectEvents(new ItemAdded("o1", "ink", 20), new ItemAdded("o1", "pen", 20)))
                .as("order matters")
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void noEventMayFollowTheOneThatDeletesTheAggregate() {
        AggregateFixture.of(new OrderAggregate())
                .given(new OrderCreated("o1", 100))
                .when(new DeleteThenAdd("o1"))
                .expectException(IllegalStateException.class,
                        e -> assertThat(e).hasMessageContaining("OrderDeleted, which deleted the aggregate"));
    }

    @Test
    void deletionIsVisibleInTheState() {
        AggregateFixture.of(new OrderAggregate())
                .given(new OrderCreated("o1", 100))
                .when(new DeleteOrder("o1"))
                .expectDeleted();
    }

    @Test
    void aDifferentEventFailsWithBothSides() {
        var result = AggregateFixture.of(new OrderAggregate())
                .given(new OrderCreated("o1", 100))
                .when(new AddItem("o1", "ink", 30));
        assertThatThrownBy(() -> result.expectEvent(new ItemAdded("o1", "ink", 31)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("\"price\":31")
                .hasMessageContaining("\"price\":30");
        assertThatThrownBy(() -> result.expectException(IllegalStateException.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ItemAdded");
    }

    @Test
    void anUnexpectedExceptionIsReportedAsTheCause() {
        var result = AggregateFixture.of(new OrderAggregate())
                .when(new CreateOrder("o1", 0));
        assertThatThrownBy(() -> result.expectEvent(new OrderCreated("o1", 0)))
                .isInstanceOf(AssertionError.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCommandTheAggregateDoesNotHandleIsAMistakeInTheTest() {
        assertThatThrownBy(() -> AggregateFixture.of(new OrderAggregate())
                .when(new com.evento.test.shop.CreateOrder("o1", 1) {
                }))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("no @AggregateCommandHandler");
    }
}
