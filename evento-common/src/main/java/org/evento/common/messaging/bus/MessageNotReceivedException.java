package org.evento.common.messaging.bus;

import org.evento.common.modeling.messaging.message.bus.NodeAddress;

import java.io.Serializable;

public class MessageNotReceivedException extends RuntimeException {

	private final NodeAddress nodeAddress;
	private final Serializable body;

	public MessageNotReceivedException(NodeAddress address, Serializable body) {
		super("Cannot send message %s to %s".formatted(body, address));
		this.nodeAddress = address;
		this.body = body;
	}

	public NodeAddress getNodeAddress() {
		return nodeAddress;
	}

	public Serializable getBody() {
		return body;
	}
}
