package org.evento.parser.model.component;

import org.evento.parser.model.handler.InvocationHandler;

import java.util.List;

public class Invoker extends Component {

	private List<InvocationHandler> invocationHandlers;

	public List<InvocationHandler> getInvocationHandlers() {
		return invocationHandlers;
	}

	public void setInvocationHandlers(List<InvocationHandler> invocationHandlers) {
		this.invocationHandlers = invocationHandlers;
	}
}
