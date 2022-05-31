package org.eventrails.parser.model.handler;

import org.eventrails.parser.model.payload.Query;

public class QueryHandler extends Handler<Query> {
	public QueryHandler(Query payload) {
		super(payload);
	}
}
