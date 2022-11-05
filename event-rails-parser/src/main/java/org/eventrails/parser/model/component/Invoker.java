package org.eventrails.parser.model.component;

import org.eventrails.parser.model.handler.InvocationHandler;
import org.eventrails.parser.model.handler.ServiceCommandHandler;

import java.util.List;

public class Invoker extends Component{

	private List<InvocationHandler> invocationHandlers;

	public List<InvocationHandler> getInvocationHandlers() {
		return invocationHandlers;
	}

	public void setInvocationHandlers(List<InvocationHandler> invocationHandlers) {
		this.invocationHandlers = invocationHandlers;
	}
}
