package org.eventrails.common.messaging.utils;

import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;

public interface AddressPicker {
	NodeAddress pickNodeAddress(String serverName);
}
