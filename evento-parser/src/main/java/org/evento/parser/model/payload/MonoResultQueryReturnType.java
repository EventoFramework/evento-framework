package org.evento.parser.model.payload;

/**
 * The MonoResultQueryReturnType class represents a query return type that returns a single result.
 * It extends the QueryReturnType class.
 */
public class MonoResultQueryReturnType extends QueryReturnType {
	/**
	 * The `MonoResultQueryReturnType` class represents a query return type that returns a single result.
	 * This class extends the `QueryReturnType` class.
	 *
	 * @param viewName The name of the view associated with the query return type.
	 */
	public MonoResultQueryReturnType(String viewName) {
		super(viewName);
	}

	/**
	 * The `MonoResultQueryReturnType` class represents a query return type that returns a single result.
	 * It extends the `QueryReturnType` class.
	 */
	public MonoResultQueryReturnType() {
		super();
	}

	/**
	 * Returns a string representation of the object.
	 * <p>
	 * The `toString` method returns the name of the view associated with the query return type.
	 *
	 * @return The name of the view associated with the query return type.
	 */
	@Override
	public String toString() {
		return getViewName();
	}
}
