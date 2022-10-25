package org.eventrails.modeling.messaging.message.bus;

public abstract class NodeAddress implements Comparable<NodeAddress> {

	private final Object address;
	private final String nodeName;
	public NodeAddress(String nodeName, Object address) {
		this.nodeName = nodeName;
		this.address = address;
	}

	public <T> T getAddress(){
		return (T) address;
	}

	public String getNodeName() {
		return nodeName;
	}
}
