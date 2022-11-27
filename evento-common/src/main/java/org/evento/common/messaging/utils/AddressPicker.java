package org.evento.common.messaging.utils;

import org.evento.common.modeling.messaging.message.bus.NodeAddress;

public interface AddressPicker<T extends NodeAddress> {
	T pickNodeAddress(String serverName);
}
