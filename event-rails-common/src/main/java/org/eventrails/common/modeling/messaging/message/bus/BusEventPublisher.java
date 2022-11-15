package org.eventrails.common.modeling.messaging.message.bus;

import org.eventrails.common.messaging.bus.MessageBus;

import java.io.IOException;

public interface BusEventPublisher {
	public void subscribe(BusEventSubscriber subscriber);
}
