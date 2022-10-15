package org.eventrails.modeling.messaging.message.bus;

import java.io.Serializable;

public abstract class ResponseSender {
	public  abstract void sendResponse(Serializable response);

	public abstract void sendError(Throwable e);
}
