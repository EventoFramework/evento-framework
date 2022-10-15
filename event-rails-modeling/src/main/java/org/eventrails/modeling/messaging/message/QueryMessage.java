package org.eventrails.modeling.messaging.message;

import org.eventrails.modeling.messaging.payload.Query;

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
