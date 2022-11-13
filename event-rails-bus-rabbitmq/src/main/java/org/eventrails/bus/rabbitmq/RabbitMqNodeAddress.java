package org.eventrails.bus.rabbitmq;

import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;

public class RabbitMqNodeAddress extends NodeAddress {
	public RabbitMqNodeAddress(String nodeName, Object address, String nodeId) {
		super(nodeName, address, nodeId);
	}

	@Override
	public int compareTo(NodeAddress o) {
		return getNodeId().compareTo(o.getNodeId());
	}
}
