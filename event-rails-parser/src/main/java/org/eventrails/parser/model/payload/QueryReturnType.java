package org.eventrails.parser.model.payload;

public abstract class QueryReturnType {

	private final String viewName;
	public QueryReturnType(String viewName) {
		this.viewName = viewName;
	}

	public String getViewName() {
		return viewName;
	}

	public abstract boolean isMultiple();

}
