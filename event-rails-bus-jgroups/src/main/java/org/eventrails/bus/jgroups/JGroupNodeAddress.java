package org.eventrails.bus.jgroups;

import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;
import org.jgroups.Address;
import org.jgroups.PhysicalAddress;
import org.jgroups.util.UUID;

public class JGroupNodeAddress extends NodeAddress {

	public JGroupNodeAddress(Address address) {
		super(address.toString(), address, addressToId(address));
	}

	private static String addressToId(Address address) {
		String addressId = address.getClass().getName() + '@' + Integer.toHexString(address.hashCode());
		if(address instanceof PhysicalAddress p)
			addressId = p.printIpAddress();
		if(address instanceof UUID p)
			addressId = p.toStringLong();
		return addressId;
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
