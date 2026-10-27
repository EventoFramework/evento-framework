package com.evento.common.modeling.messaging.message.application;

import com.evento.common.modeling.messaging.payload.Query;

/**
 * The QueryMessage class represents a message containing a query.
 *
 * @param <T> The type of the query payload.
 */
public class QueryMessage<T extends Query<?>> extends Message<T> {
	/**
	 * The QueryMessage class represents a message containing a query.
	 *
     * @param payload The Query payload
     */
	public QueryMessage(T payload) {
		super(payload);
	}

	/**
	 * The QueryMessage class represents a message containing a query.
	 * It is a subclass of the Message class and is generically typed with a Query payload.
	 * The QueryMessage class provides methods to retrieve the query name and query payload.
	 *
     */
	public QueryMessage() {
	}


	/**
	 * The writes the answer must reflect (3.0, additive): the answering bundle waits until
	 * its projectors have processed them. Null or empty: answered from the read model as it
	 * is. A bundle that predates the field ignores it.
	 */
	private com.evento.common.modeling.messaging.query.ConsistencyToken consistency;

	/**
	 * Retrieves the name of the query.
	 *
	 * @return The name of the query as a String.
	 */
	public String getQueryName() {
		return super.getPayloadName();
	}

	/** The writes the answer must reflect, or null. */
	public com.evento.common.modeling.messaging.query.ConsistencyToken getConsistency() {
		return consistency;
	}

	public void setConsistency(com.evento.common.modeling.messaging.query.ConsistencyToken consistency) {
		this.consistency = consistency == null || consistency.isEmpty() ? null : consistency;
	}

	@Override
	public String toString() {
		return "QueryMessage{} " + super.toString();
	}
}
