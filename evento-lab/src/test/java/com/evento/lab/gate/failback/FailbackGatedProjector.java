package com.evento.lab.gate.failback;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.lab.api.event.OrderCreatedEvent;
import com.evento.lab.gate.GateStore;

/**
 * Holds the bundle's startup gate until the test releases {@link GateStore#gate}:
 * the bundle is enabled, and its sagas/observers started, only once this has
 * consumed everything up to the head.
 */
@Projector(version = 1, waitForHeadReached = true)
public class FailbackGatedProjector {

    @EventHandler
    void on(OrderCreatedEvent e) throws InterruptedException {
        GateStore.handle(e.getOrderId());
    }
}
