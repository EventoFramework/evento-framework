package org.evento.common.modeling.messaging.message.application;

import org.evento.common.modeling.messaging.payload.ServiceEvent;

/**
 * ServiceEventMessage is a class that represents a message containing a service event payload.
 * It extends the EventMessage class.
 *
 * @see EventMessage
 */
public class ServiceEventMessage extends EventMessage<ServiceEvent> {
	/**
	 * ServiceEventMessage is a class that represents a message containing a service event payload.
     * @param payload The Service Event carried
     */
	public ServiceEventMessage(ServiceEvent payload) {
		super(payload);
	}

	/**
	 * ServiceEventMessage is a class that represents a message containing a service event payload.
	 * It extends the EventMessage class.
	 *
	 * @see EventMessage
	 */
	public ServiceEventMessage() {
	}
}
