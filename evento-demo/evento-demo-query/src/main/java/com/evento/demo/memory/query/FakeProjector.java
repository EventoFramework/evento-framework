package com.evento.demo.memory.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.demo.api.event.DemoCreatedEvent;
import com.evento.demo.api.utils.Utils;

//@Projector(version =1)
public class FakeProjector {

    @EventHandler
    public void on(DemoCreatedEvent event) {
        Utils.logMethodFlow(this, "on", event, "IGNORE EVENT");
    }
}
