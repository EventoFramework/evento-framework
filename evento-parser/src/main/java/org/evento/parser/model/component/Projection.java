package org.evento.parser.model.component;

import org.evento.parser.model.handler.QueryHandler;

import java.util.List;

/**
 * The {@code Projection} class represents a component that handles projections.
 * It extends the {@code Component} class.
 *
 * <p>
 * The projections are handled by a list of {@code QueryHandler}s.
 * A {@code QueryHandler} is responsible for managing a specific query and its invocations.
 * </p>
 *
 */
public class Projection extends Component {

	private List<QueryHandler> queryHandlers;

	/**
	 * Returns a list of {@code QueryHandler} objects.
	 *
	 * <p>
	 * This method retrieves the {@code queryHandlers} field from the {@code Projection} class.
	 * The {@code queryHandlers} field stores a list of {@code QueryHandler} objects that are responsible for managing specific queries and their invocations.
	 * </p>
	 *
	 * <p>
	 * To use this method, create an instance of the {@code Projection} class and call the {@code getQueryHandlers} method.
	 * </p>
	 *
	 * @return a list of {@code QueryHandler} objects
	 *
	 * @see Projection
	 * @see QueryHandler
	 */
	public List<QueryHandler> getQueryHandlers() {
		return queryHandlers;
	}

	/**
	 * Sets the list of {@code QueryHandler} objects.
	 *
	 * <p>
	 * This method sets the list of {@code QueryHandler} objects for the {@code Projection} class.
	 * These {@code QueryHandler} objects are responsible for managing specific queries and their invocations.
	 * </p>
	 *
	 * <p>
	 * To use this method, create a list of {@code QueryHandler} objects and pass it as a parameter to this method.
	 * </p>
	 *
	 * @param queryHandlers a list of {@code QueryHandler} objects
	 *
	 * @see Projection
	 * @see QueryHandler
	 */
	public void setQueryHandlers(List<QueryHandler> queryHandlers) {
		this.queryHandlers = queryHandlers;
	}
}
