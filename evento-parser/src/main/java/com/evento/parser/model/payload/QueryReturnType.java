package com.evento.parser.model.payload;

import java.io.Serializable;

/**
 * The QueryReturnType class is an abstract class representing a query return type.
 * It implements the Serializable interface to support object serialization and deserialization.
 *
 * @version 1.0
 */
public abstract class QueryReturnType implements Serializable {

	private String viewName;

	/**
	 * The QueryReturnType class represents a query return type.
     * @param viewName the view name
     */
	public QueryReturnType(String viewName) {
		this.viewName = viewName;
	}

	/**
	 * The QueryReturnType class represents a query return type.
	 * It is an abstract class that implements the Serializable interface for object serialization and deserialization.
	 */
	public QueryReturnType() {
	}

	/**
	 * Returns the name of the view associated with the query return type.
	 *
	 * @return The name of the view.
	 */
	public String getViewName() {
		return viewName;
	}

	/**
	 * Sets the name of the view associated with the query return type.
	 *
	 * @param viewName The name of the view.
	 */
	public void setViewName(String viewName) {
		this.viewName = viewName;
	}


}
