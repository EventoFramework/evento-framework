package org.eventrails.parser.model.payload;

public class MonoResultQueryReturnType extends QueryReturnType {
	public MonoResultQueryReturnType(String viewName) {
		super(viewName);
	}

	public MonoResultQueryReturnType() {
		super();
	}

	@Override
	public String toString() {
		return getViewName();
	}
}
