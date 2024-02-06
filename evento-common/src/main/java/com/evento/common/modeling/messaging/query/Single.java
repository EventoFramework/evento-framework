package com.evento.common.modeling.messaging.query;

import com.evento.common.modeling.messaging.payload.View;

/**
 * Creates a new instance of the Single class.
 * @param <T> the view response of a query
 */
public class Single<T extends View> extends QueryResponse<T> {

	private T data;

	/**
	 * The Single class represents a response containing a single view object.
	 * It extends the QueryResponse class.
	 * <p>
	 * This class is generic and the type parameter T must extend the View class.
	 * It provides methods to set and retrieve the data from the response.
     */
	public Single() {
	}

	/**
	 * Creates a new Single object with the given data.
	 *
	 * @param <R>  the type of the data in the response, must extend the View class
	 * @param data the data to set in the response
	 * @return the Single object with the provided data
	 */
	public static <R extends View> Single<R> of(R data) {
		var r = new Single<R>();
		r.setData(data);
		return r;
	}

	/**
	 * Retrieves the data stored in the response object.
	 *
	 * @return the data stored in the response object
	 */
	public T getData() {
		return data;
	}

	/**
	 * Sets the data in the response object.
	 *
	 * @param data the data to be set in the response object. It must extend the View class.
	 */
	public void setData(T data) {
		this.data = data;
	}


}
