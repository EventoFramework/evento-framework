package org.eventrails.modeling.messaging.message.bus;

public abstract class NodeAddress {

	private final Object address;

	public NodeAddress(Object address) {
		this.address = address;
	}

	public <T> T getAddress(){
		return (T) address;
	}
}
