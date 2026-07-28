package com.evento.lab.gate.nowait;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.lab.api.event.OrderCreatedEvent;
import com.evento.lab.gate.GateStore;

/**
 * Default {@code waitForHeadReached} (false): the bundle must enable even while this
 * projector is still blocked far behind the event stream head.
 */
@Projector(version = 1)
public class NoWaitProjector {

    @EventHandler
    void on(OrderCreatedEvent e) throws InterruptedException {
        GateStore.handle(e.getOrderId());
    }
}
