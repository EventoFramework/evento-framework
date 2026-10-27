package com.evento.test;

import com.evento.test.shop.Charge;
import com.evento.test.shop.ItemAdded;
import com.evento.test.shop.Notify;
import com.evento.test.shop.OrderBoard;
import com.evento.test.shop.OrderCharged;
import com.evento.test.shop.OrderCreated;
import com.evento.test.shop.PaymentService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectorAndServiceFixtureTest {

    @Test
    void aProjectorUpdatesItsRepositoryAndSendsWhatItSends() {
        var items = new HashMap<String, Integer>();
        ProjectorFixture.of(new OrderBoard(items))
                .given(new OrderCreated("o1", 100))
                .whenEvent(new ItemAdded("o1", "pen", 3))
                .expectCommands(new Notify("o1"));
        assertThat(items).containsEntry("o1", 1);
    }

    @Test
    void onAReplayTheProjectorSeesItIsNotLive() {
        var items = new HashMap<String, Integer>();
        ProjectorFixture.of(new OrderBoard(items))
                .replaying()
                .given(new OrderCreated("o1", 100))
                .whenEvent(new ItemAdded("o1", "pen", 3))
                .expectNoCommands();
        assertThat(items).containsEntry("o1", 1);
    }

    @Test
    void aProjectorThatThrowsIsTheResultsException() {
        ProjectorFixture.of(new OrderBoard())
                .given(new OrderCreated("o1", 100))
                .whenEvent(new OrderCreated("o1", 100))
                .expectException(IllegalStateException.class, e -> assertThat(e).hasMessageContaining("duplicate"));
    }

    @Test
    void anEventTheProjectorDoesNotHandleIsAMistakeInTheTest() {
        assertThatThrownBy(() -> ProjectorFixture.of(new OrderBoard()).whenEvent(new OrderCharged("o1", 1)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("no @EventHandler for OrderCharged");
    }

    @Test
    void aServiceCommandProducesItsEvent() {
        var service = new PaymentService();
        ServiceFixture.of(service)
                .when(new Charge("o1", 30))
                .expectEvent(new OrderCharged("o1", 30));
        assertThat(service.charged).containsExactly("o1");
    }

    @Test
    void aServiceCommandWithoutAnEventAndOneThatFails() {
        ServiceFixture.of(new PaymentService()).when(new Notify("o1")).expectNoEvent();
        ServiceFixture.of(new PaymentService()).when(new Charge("o1", 0)).expectException(IllegalArgumentException.class);
    }
}
