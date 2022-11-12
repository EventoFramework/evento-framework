package org.eventrails.modeling.messaging.utils;

import org.eventrails.modeling.messaging.message.bus.NodeAddress;

public interface AddressPicker {
	NodeAddress pickNodeAddress(String serverName);
}
