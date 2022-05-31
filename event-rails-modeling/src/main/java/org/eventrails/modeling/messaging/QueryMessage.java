package org.eventrails.modeling.messaging;

import org.eventrails.modeling.messaging.payload.Query;

public class QueryMessage extends Message<Query> {
	public QueryMessage(Query payload) {
		super(payload);
	}
}
