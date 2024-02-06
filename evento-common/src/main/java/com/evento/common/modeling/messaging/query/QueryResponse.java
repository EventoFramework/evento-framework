package com.evento.common.modeling.messaging.query;

import com.evento.common.modeling.messaging.payload.View;

import java.io.Serializable;

/**
 * The QueryResponse class is an abstract class that represents a response object for a query.
 * It is Serializable, which means it can be converted into a byte stream and sent over a network or stored in a file.
 * The class is generic, with a type parameter T that must extend the View class.
 * It provides methods to set and retrieve the data from the response.
 *
 * @param <T> The type of view the QueryResponse object contains.
 */
public abstract class QueryResponse<T extends View> implements Serializable {


}
