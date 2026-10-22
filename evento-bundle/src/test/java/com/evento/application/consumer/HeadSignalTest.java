package com.evento.application.consumer;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;

/** A head notice wakes the consumers that handle one of the events it names, and only those. */
class HeadSignalTest {

    @Test
    void aNoticeWakesOnlyTheConsumersThatHandleOneOfItsEvents() {
        var signal = new HeadSignal(() -> true);
        var orders = new Semaphore(0);
        var stock = new Semaphore(0);
        var any = new Semaphore(0);
        signal.register(orders, Set.of("OrderCreatedEvent", "OrderPaidEvent"));
        signal.register(stock, Set.of("StockMovedEvent"));
        signal.register(any);

        signal.announce(List.of("OrderPaidEvent"));

        assertThat(orders.availablePermits()).isEqualTo(1);
        assertThat(stock.availablePermits()).isZero();
        assertThat(any.availablePermits()).as("a consumer with no names takes every notice").isEqualTo(1);
    }

    @Test
    void aNoticeThatNamesNothingWakesEveryone() {
        var signal = new HeadSignal(() -> true);
        var stock = new Semaphore(0);
        signal.register(stock, Set.of("StockMovedEvent"));
        signal.announce(List.of());
        assertThat(stock.availablePermits()).isEqualTo(1);
    }

    @Test
    void repeatedNoticesLeaveOnePermitAndAnUnregisteredConsumerNone() {
        var signal = new HeadSignal(() -> true);
        var w = new Semaphore(0);
        signal.register(w);
        signal.announce();
        signal.announce();
        assertThat(w.availablePermits()).isEqualTo(1);
        w.drainPermits();
        signal.unregister(w);
        signal.announce();
        assertThat(w.availablePermits()).isZero();
    }

    @Test
    void theBodyIsTheHeadThenOneNamePerLine() {
        var names = "OrderCreatedEvent\nOrderPaidEvent".getBytes(StandardCharsets.UTF_8);
        var body = ByteBuffer.allocate(Long.BYTES + names.length).putLong(7).put(names).array();
        assertThat(HeadSignal.namesOf(body)).containsExactly("OrderCreatedEvent", "OrderPaidEvent");
        assertThat(HeadSignal.namesOf(ByteBuffer.allocate(Long.BYTES).putLong(7).array())).isEmpty();
    }

    @Test
    void theEventsOfAComponentComeFromItsManagersHandlerMap() {
        Map<String, HashMap<String, Object>> handlers = new HashMap<>();
        handlers.put("OrderCreatedEvent", new HashMap<>(Map.of("OrderProjector", 1, "StockProjector", 2)));
        handlers.put("StockMovedEvent", new HashMap<>(Map.of("StockProjector", 3)));
        assertThat(HeadSignal.handledBy(handlers, "OrderProjector")).containsExactly("OrderCreatedEvent");
        assertThat(HeadSignal.handledBy(handlers, "StockProjector"))
                .containsExactlyInAnyOrder("OrderCreatedEvent", "StockMovedEvent");
    }

    @Test
    void withoutNoticesTheConsumerPausesOnlyThePollingPause() {
        var signal = HeadSignal.polling();
        var w = new Semaphore(0);
        long start = System.nanoTime();
        signal.pauseAtHead(w, 20, 10_000);
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2_000);
    }
}
