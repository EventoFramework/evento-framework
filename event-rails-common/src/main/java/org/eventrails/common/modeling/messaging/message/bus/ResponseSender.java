package org.eventrails.common.modeling.messaging.message.bus;

import java.io.Serializable;

public interface ResponseSender {
	void sendResponse(Serializable response);

	void sendError(Throwable e);
}
