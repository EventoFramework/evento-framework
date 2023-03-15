package org.evento.application.proxy;

import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.messaging.gateway.QueryGateway;

public abstract class InvokerWrapper {

	public CommandGateway getCommandGateway(){
		throw new RuntimeException();
	}

	public QueryGateway getQueryGateway(){
		throw new RuntimeException();
	}

}
