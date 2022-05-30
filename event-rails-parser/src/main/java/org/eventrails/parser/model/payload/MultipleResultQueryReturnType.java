package org.eventrails.parser.model.payload;

public class MultipleResultQueryReturnType extends QueryReturnType {
	public MultipleResultQueryReturnType(String viewName) {
		super(viewName);
	}

	@Override
	public String toString() {
		return "Multiple<" + getViewName() + ">";
	}
}
