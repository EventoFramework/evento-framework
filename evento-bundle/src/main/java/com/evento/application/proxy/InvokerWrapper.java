package com.evento.application.proxy;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;

/**
 * The InvokerWrapper class is an abstract class that serves as a wrapper for the command and query gateways.
 * It provides methods to retrieve the command and query gateways.
 */
public abstract class InvokerWrapper {

	/**
	 * Retrieves the command gateway.
	 *
	 * @return the command gateway
	 * @throws RuntimeException if the command gateway is unavailable.
	 */
	protected CommandGateway getCommandGateway() {
		throw new RuntimeException();
	}

	/**
	 * Retrieves the query gateway.
	 *
	 * @return the query gateway
	 * @throws RuntimeException if the query gateway is unavailable.
	 */
	protected QueryGateway getQueryGateway() {
		throw new RuntimeException();
	}

}
