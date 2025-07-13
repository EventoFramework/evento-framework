package com.evento.demo.memory.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.demo.api.event.DemoCreatedEvent;
import com.evento.demo.api.event.DemoDeletedEvent;
import com.evento.demo.api.event.DemoUpdatedEvent;
import com.evento.demo.api.utils.Utils;

@Projector(version =1)
public class Fake1Projector {

    @EventHandler(retry = 3)
    void on(DemoCreatedEvent event) {
        Utils.logMethodFlow(this, "on", event, "HIT");
    }

    @EventHandler
    void on(DemoUpdatedEvent event) {
        Utils.logMethodFlow(this, "on", event, "HIT");

    }

    @EventHandler
    void on(DemoDeletedEvent event) {
        Utils.logMethodFlow(this, "on", event, "HIT");

    }
}
