package org.eventrails.parser.model.payload;

public class MonoResultQueryReturnType extends QueryReturnType {
	public MonoResultQueryReturnType(String viewName) {
		super(viewName);
	}

	@Override
	public String toString() {
		return getViewName();
	}
}
