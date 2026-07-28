package com.evento.lab.gate.waiting;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.lab.api.event.OrderCreatedEvent;
import com.evento.lab.gate.GateStore;

/**
 * Opts into the startup gate: the bundle must stay disabled until this projector has
 * consumed everything up to the event stream head.
 */
@Projector(version = 1, waitForHeadReached = true)
public class WaitingProjector {

    @EventHandler
    void on(OrderCreatedEvent e) throws InterruptedException {
        GateStore.handle(e.getOrderId());
    }
}
