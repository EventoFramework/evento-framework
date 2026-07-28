package com.evento.lab.async;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.lab.api.event.OrderCreatedEvent;
import com.evento.lab.api.event.OrderUpdatedEvent;

/**
 * Projector with one parallel handler and one sequential handler, so the ITs can exercise
 * both paths — and the barrier between them — in a single consumer.
 *
 * <p>Gates bundle-enable on head alignment: {@code bundleIsNotEnabledUntilAsyncHandlersHaveFinished}
 * asserts that a gating projector drains its async handlers before the bundle becomes available.
 */
@Projector(version = 1, waitForHeadReached = true)
public class AsyncLabProjector {

    public static final String EXECUTOR = "lab-async";

    /** Parallel: dispatched to the {@code lab-async} executor. */
    @EventHandler(executor = EXECUTOR, retry = 0)
    void on(OrderCreatedEvent e) throws InterruptedException {
        AsyncLabStore.enter();
        try {
            AsyncLabStore.threadNames.put(e.getOrderId() + ":handler", Thread.currentThread().getName());
            AsyncLabStore.transactionSeenByHandler.put(e.getOrderId(),
                    String.valueOf(TransactionTrackingInterceptor.currentTransaction()));

            var gate = AsyncLabStore.gate;
            if (gate != null) gate.await();
            if (AsyncLabStore.handlerDelayMillis > 0) Thread.sleep(AsyncLabStore.handlerDelayMillis);

            if (AsyncLabStore.failFor.contains(e.getOrderId())) {
                throw new IllegalStateException("deliberate failure for " + e.getOrderId());
            }
            AsyncLabStore.applied.add(e.getOrderId());
            // quantity carries the event's position within its aggregate, so an ordering
            // test can assert the per-aggregate sequence without needing the envelope.
            AsyncLabStore.recordOrder(e.getOrderId(), e.getQuantity());
        } finally {
            AsyncLabStore.exit();
        }
    }

    /** Sequential: no executor, so the consume loop runs it inline. */
    @EventHandler
    void on(OrderUpdatedEvent e) {
        AsyncLabStore.threadNames.put(e.getOrderId() + ":inline", Thread.currentThread().getName());
        AsyncLabStore.applied.add("inline:" + e.getOrderId());
    }
}
