package com.evento.lab.gate.failback;

import com.evento.common.modeling.annotations.component.Observer;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.lab.api.event.OrderCreatedEvent;

/** Its engine starts after the startup gate opens; the test checks that it did. */
@Observer(version = 1)
public class FailbackObserver {

    @EventHandler
    void on(OrderCreatedEvent e) {
        // nothing to do: only the engine's existence is asserted
    }
}
