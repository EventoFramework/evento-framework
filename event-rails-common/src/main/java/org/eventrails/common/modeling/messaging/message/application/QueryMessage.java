package org.eventrails.common.modeling.messaging.message.application;

import org.eventrails.common.modeling.messaging.payload.Query;

public class QueryMessage<T extends Query<?>> extends Message<T>  {
	public QueryMessage(T payload) {
		super(payload);
	}

	public QueryMessage() {
	}


	public String getQueryName() {
		return super.getPayloadName();
	}
}
