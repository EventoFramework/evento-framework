package com.evento.common.messaging.gateway;

import java.util.concurrent.TimeUnit;

/**
 * The Gateway interface defines common methods for interacting with different types of message gateways.
 */
public interface Gateway {

	/**
	 * Returns the default timeout time for gateway operations.
	 * @return the default timeout time in the default time unit
	 */
	default long getDefaultTimeoutTime() {
		return 30;
	}
	default TimeUnit getDefaultTimeoutUnit(){
		return TimeUnit.SECONDS;
	}

}
