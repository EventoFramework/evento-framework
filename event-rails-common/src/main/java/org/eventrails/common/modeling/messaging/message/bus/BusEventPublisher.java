package org.eventrails.common.modeling.messaging.message.bus;

public interface BusEventPublisher {
	public void subscribe(BusEventSubscriber subscriber);
}
