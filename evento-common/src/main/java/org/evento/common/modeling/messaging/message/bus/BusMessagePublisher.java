package org.evento.common.modeling.messaging.message.bus;

public interface BusMessagePublisher {
	public void subscribe(BusMessageSubscriber subscriber);
}
