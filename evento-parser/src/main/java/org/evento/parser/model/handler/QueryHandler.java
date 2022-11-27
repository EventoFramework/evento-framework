package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Query;

public class QueryHandler extends Handler<Query> {
	public QueryHandler(Query payload) {
		super(payload);
	}

	public QueryHandler() {
	}
}
