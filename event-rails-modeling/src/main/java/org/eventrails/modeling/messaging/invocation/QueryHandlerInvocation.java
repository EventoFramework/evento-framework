package org.eventrails.modeling.messaging.invocation;

import org.eventrails.modeling.messaging.message.QueryMessage;
import org.eventrails.modeling.messaging.message.ServiceCommandMessage;

public class QueryHandlerInvocation {
	private QueryMessage<?> queryMessage;

	public QueryHandlerInvocation(QueryMessage<?> queryMessage) {
		this.queryMessage = queryMessage;
	}

	public QueryHandlerInvocation() {
	}

	public QueryMessage<?> getQueryMessage() {
		return queryMessage;
	}

	public void setQueryMessage(QueryMessage<?> queryMessage) {
		this.queryMessage = queryMessage;
	}
}
