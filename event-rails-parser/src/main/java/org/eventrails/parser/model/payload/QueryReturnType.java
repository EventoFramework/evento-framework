package org.eventrails.parser.model.payload;

import java.io.Serializable;

public abstract class QueryReturnType implements Serializable {

	private String viewName;
	public QueryReturnType(String viewName) {
		this.viewName = viewName;
	}

	public QueryReturnType() {
	}

	public String getViewName() {
		return viewName;
	}

	public void setViewName(String viewName) {
		this.viewName = viewName;
	}


}
