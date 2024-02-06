package com.evento.demo.observer;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.modeling.annotations.component.Observer;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.demo.api.event.DemoDeletedEvent;
import com.evento.demo.api.event.DemoUpdatedEvent;
import com.evento.demo.api.utils.Utils;

@Observer(version = 1)
public class DemoObserver {

	@EventHandler
	public void on(DemoUpdatedEvent event, CommandGateway commandGateway) {
		Utils.logMethodFlow(this, "on", event, "OBSERVED");
	}

	@EventHandler
	public void on(DemoDeletedEvent event, CommandGateway commandGateway) {
		Utils.logMethodFlow(this, "on", event, "OBSERVED");
	}
}
