package org.eventrails.common.messaging.bus.rabbitmq;


import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.common.messaging.bus.MessageBus;

import java.io.Serializable;
import java.util.Set;

public class RabbitMqMessageBus extends MessageBus {

	public RabbitMqMessageBus() {
		super(null);
	}

	@Override
	public void cast(NodeAddress address, Serializable message) throws Exception {

	}

	@Override
	public void broadcast(Serializable message) throws Exception {

	}

	@Override
	public NodeAddress getAddress() {
		return null;
	}

	@Override
	public Set<NodeAddress> getCurrentView() {
		return null;
	}
}
