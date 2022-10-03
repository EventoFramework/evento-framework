package org.eventrails.parser.model.node;

import org.eventrails.parser.model.handler.QueryHandler;

import java.util.List;

public class Projection extends Node {

	private List<QueryHandler> queryHandlers;

	public void setQueryHandlers(List<QueryHandler> queryHandlers) {
		this.queryHandlers = queryHandlers;
	}

	public List<QueryHandler> getQueryHandlers() {
		return queryHandlers;
	}
}
