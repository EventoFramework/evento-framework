package org.evento.common.performance;

import org.evento.common.messaging.bus.MessageBus;

public class RemotePerformanceService extends PerformanceService {

	private final MessageBus messageBus;
	private final String serverNodeName;

	public RemotePerformanceService(MessageBus messageBus,
									String serverNodeName) {
		super();
		this.messageBus = messageBus;
		this.serverNodeName = serverNodeName;
	}

	public RemotePerformanceService(MessageBus messageBus,
									String serverNodeName,
									double rate) {
		super(rate);
		this.messageBus = messageBus;
		this.serverNodeName = serverNodeName;
	}


	@Override
	public void sendServiceTimeMetricMessage(PerformanceServiceTimeMessage message) throws Exception {
		messageBus.cast(messageBus.findNodeAddress(serverNodeName), message);
	}

	@Override
	public void sendInvocationMetricMessage(PerformanceInvocationsMessage message) throws Exception {
		messageBus.cast(messageBus.findNodeAddress(serverNodeName), message);
	}


}
