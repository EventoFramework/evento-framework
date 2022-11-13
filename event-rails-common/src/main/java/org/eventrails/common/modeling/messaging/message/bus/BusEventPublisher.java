package org.eventrails.common.modeling.messaging.message.bus;

import java.io.IOException;

public interface BusEventPublisher {
	public void subscribe(BusEventSubscriber subscriber);
}
