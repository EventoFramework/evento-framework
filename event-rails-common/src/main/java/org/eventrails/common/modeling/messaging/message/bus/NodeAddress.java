package org.eventrails.common.modeling.messaging.message.bus;

public abstract class NodeAddress implements Comparable<NodeAddress> {

	private final Object address;
	private final String nodeName;
	private final String nodeId;
	public NodeAddress(String nodeName, Object address, String nodeId) {
		this.nodeName = nodeName;
		this.address = address;
		this.nodeId = nodeId;
	}

	public <T> T getAddress(){
		return (T) address;
	}

	public String getNodeName() {
		return nodeName;
	}

	public String getNodeId() {
		return nodeId;
	}

	@Override
	public String toString() {
		return nodeName;
	}
}
