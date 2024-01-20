package org.evento.parser.model.component;

import org.evento.parser.model.handler.InvocationHandler;

import java.util.List;

/**
 * The {@code Invoker} class represents a component that handles invocations. It extends the {@code Component} class.
 * It provides a list of invocation handlers that can be set and accessed.
 */
public class Invoker extends Component {

	private List<InvocationHandler> invocationHandlers;

	/**
	 * Retrieves the list of invocation handlers associated with this Invoker object.
	 *
	 * @return A List of InvocationHandler objects representing the invocation handlers.
	 */
	public List<InvocationHandler> getInvocationHandlers() {
		return invocationHandlers;
	}

	/**
	 * Sets the list of invocation handlers.
	 *
	 * @param invocationHandlers the list of invocation handlers to be set. It should not be null.
	 */
	public void setInvocationHandlers(List<InvocationHandler> invocationHandlers) {
		this.invocationHandlers = invocationHandlers;
	}
}
