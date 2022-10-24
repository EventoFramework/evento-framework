package org.eventrails.parser.model.payload;

public class MultipleResultQueryReturnType extends QueryReturnType {
	public MultipleResultQueryReturnType(String viewName) {
		super(viewName);
	}

	public MultipleResultQueryReturnType() {
		super();
	}


	@Override
	public String toString() {
		return "Multiple<" + getViewName() + ">";
	}
}
