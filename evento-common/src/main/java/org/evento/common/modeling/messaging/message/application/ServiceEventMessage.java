package org.evento.common.modeling.messaging.message.application;

import org.evento.common.modeling.messaging.payload.ServiceEvent;

public class ServiceEventMessage extends EventMessage<ServiceEvent> {
	public ServiceEventMessage(ServiceEvent payload) {
		super(payload);
	}

	public ServiceEventMessage() {
	}
}
