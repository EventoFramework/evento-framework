package org.eventrails.modeling.messaging.message.bus;

public class ServerHandleInvocationMessage extends InvocationMessage {

	public ServerHandleInvocationMessage(String payload) {
		this.payload = payload;
	}

	public ServerHandleInvocationMessage() {
	}

}
