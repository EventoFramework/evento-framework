package org.eventrails.parser.model.component;

import org.eventrails.parser.model.handler.QueryHandler;

import java.util.List;

public class Projection extends Component {

	private List<QueryHandler> queryHandlers;

	public void setQueryHandlers(List<QueryHandler> queryHandlers) {
		this.queryHandlers = queryHandlers;
	}

	public List<QueryHandler> getQueryHandlers() {
		return queryHandlers;
	}
}
