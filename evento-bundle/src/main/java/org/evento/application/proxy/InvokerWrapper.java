package org.evento.application.proxy;

import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.messaging.gateway.QueryGateway;

public abstract class InvokerWrapper {

	protected CommandGateway getCommandGateway() {
		throw new RuntimeException();
	}

	protected QueryGateway getQueryGateway() {
		throw new RuntimeException();
	}

}
