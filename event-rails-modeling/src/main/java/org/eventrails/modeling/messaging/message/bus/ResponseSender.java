package org.eventrails.modeling.messaging.message.bus;

public abstract class ResponseSender {
	public  abstract void sendResponse(Object response);

	public abstract void sendError(Throwable e);
}
