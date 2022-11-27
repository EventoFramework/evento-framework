package org.evento.common.modeling.messaging.message.bus;

public interface BusEventPublisher {
	public void subscribe(BusEventSubscriber subscriber);
}
