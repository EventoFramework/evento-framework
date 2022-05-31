package org.eventrails.demo.query;

import org.eventrails.demo.api.event.DemoCreatedEvent;
import org.eventrails.demo.api.event.DemoDeletedEvent;
import org.eventrails.demo.api.event.DemoUpdatedEvent;
import org.eventrails.modeling.annotations.handler.EventHandler;
import org.eventrails.modeling.messaging.EventMessage;
import org.eventrails.modeling.annotations.component.Projector;
import org.eventrails.modeling.gateway.QueryGateway;

@Projector
public class DemoProjector {

	@EventHandler
	void on(DemoCreatedEvent event, QueryGateway queryGateway, EventMessage eventMessage){

	}

	@EventHandler
	void on(DemoUpdatedEvent event){

	}

	@EventHandler
	void on(DemoDeletedEvent event){

	}
}
