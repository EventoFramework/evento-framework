package org.eventrails.common.modeling.messaging.message.application;

import org.eventrails.common.modeling.messaging.payload.ServiceEvent;

public class ServiceEventMessage extends EventMessage<ServiceEvent> {
	public ServiceEventMessage(ServiceEvent payload) {
		super(payload);
	}

	public ServiceEventMessage() {
	}
}
