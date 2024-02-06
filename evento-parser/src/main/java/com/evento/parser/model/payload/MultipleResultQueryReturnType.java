package com.evento.parser.model.payload;

/**
 * Represents a query return type that can have multiple results.
 * Extends the QueryReturnType class and provides methods to get and set the view name.
 */
public class MultipleResultQueryReturnType extends QueryReturnType {
	/**
	 * Represents a query return type that can have multiple results.
	 * Extends the QueryReturnType class and provides methods to get and set the view name.
	 *
	 * @param viewName The name of the view associated with the query return type.
	 */
	public MultipleResultQueryReturnType(String viewName) {
		super(viewName);
	}

	/**
	 * Represents a query return type that can have multiple results.
	 * Extends the QueryReturnType class and provides methods to get and set the view name.
	 */
	public MultipleResultQueryReturnType() {
		super();
	}


	@Override
	public String toString() {
		return "Multiple<" + getViewName() + ">";
	}
}
