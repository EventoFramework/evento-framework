package org.evento.common.modeling.messaging.query;

import org.evento.common.modeling.messaging.payload.View;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Represents a response object that contains multiple instances of a specified type.
 * Extends the `QueryResponse` class.
 *
 * @param <T> The type of view the Multiple object contains.
 */
public class Multiple<T extends View> extends QueryResponse<T> {

	private Collection<T> data;

	/**
	 * Represents a response object that contains multiple instances of a specified type.
	 * Extends the `QueryResponse` class.
	 *
     */
	public Multiple() {
	}

	/**
	 * Constructs a new Multiple object containing multiple instances of a specified type.
	 *
	 * @param <R> The type of view the Multiple object contains.
	 * @param data The collection of views to be contained in the Multiple object.
	 * @return A new Multiple object containing the specified views.
	 */
	public static <R extends View> Multiple<R> of(Collection<R> data) {
		var r = new Multiple<R>();
		r.setData(new ArrayList<>(data));
		return r;
	}

	/**
	 * Constructs a new Multiple object containing multiple instances of a specified type.
	 *
	 * @param <R> The type of view the Multiple object contains.
	 * @param items The array of views to be contained in the Multiple object.
	 * @return A new Multiple object containing the specified views.
	 */
	@SafeVarargs
	public static <R extends View> Multiple<R> of(R... items) {
		var r = new Multiple<R>();
		r.setData(List.of(items));
		return r;
	}

	/**
	 * Retrieves the data contained in the Multiple object.
	 *
	 * @return The collection of views contained in the Multiple object.
	 */
	public Collection<T> getData() {
		return data;
	}

	/**
	 * Sets the data for the Multiple object.
	 *
	 * @param data The collection of views to be set as the data for the Multiple object.
	 */
	public void setData(Collection<T> data) {
		this.data = data;
	}

}
