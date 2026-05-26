package com.evento.lab.bundle.consumer;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.lab.api.event.OrderCreatedEvent;
import com.evento.lab.api.event.OrderUpdatedEvent;

/**
 * Demonstrates a projector that always fails on updates (retry=0, no retries).
 * Created events pass through; updated events are permanently dead-lettered.
 */
@Projector(version = 1)
public class LabAlwaysFailProjector {

    @EventHandler
    void on(OrderCreatedEvent event) {
        System.out.println(getClass() + " PASS for created=" + event.getOrderId());
    }

    @EventHandler(retry = 0)
    void on(OrderUpdatedEvent event, Long eventSequenceNumber) {
        System.out.println(getClass() + " ALWAYS FAIL seq=" + eventSequenceNumber);
        throw new RuntimeException("LabAlwaysFailProjector: always fails on update");
    }
}
