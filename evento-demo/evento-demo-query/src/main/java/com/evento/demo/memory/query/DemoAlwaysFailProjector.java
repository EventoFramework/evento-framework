package com.evento.demo.memory.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.demo.api.event.DemoCreatedEvent;
import com.evento.demo.api.event.DemoUpdatedEvent;
import com.evento.demo.api.utils.Utils;

import java.util.HashMap;

@Projector(version =1)
public class DemoAlwaysFailProjector {

    @EventHandler
	void on(DemoCreatedEvent event,
            Long eventSequenceNumber) {
        Utils.logMethodFlow(this, "on", event, "ALWAYS FAIL PASS");

	}
    @EventHandler
	void on(DemoUpdatedEvent event,
            Long eventSequenceNumber) {
        Utils.logMethodFlow(this, "on", event, "ALWAYS FAIL : " + eventSequenceNumber);
		throw new RuntimeException("FAIL FOR TEST");

	}

}
