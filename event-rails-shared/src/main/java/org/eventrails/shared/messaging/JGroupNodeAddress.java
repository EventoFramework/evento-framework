package org.eventrails.shared.messaging;

import org.eventrails.modeling.messaging.message.bus.NodeAddress;
import org.jgroups.Address;

public class JGroupNodeAddress extends NodeAddress {

	public JGroupNodeAddress(Address address) {
		super(address);
	}

	@Override
	public Address getAddress() {
		return super.getAddress();
	}

	@Override
	public int compareTo(NodeAddress o) {
		return getAddress().compareTo(o.getAddress());
	}
}
