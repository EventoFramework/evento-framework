package org.eventrails.common.messaging.utils;

import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;

public interface AddressPicker<T extends NodeAddress> {
	T pickNodeAddress(String serverName);
}
