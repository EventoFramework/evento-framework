package org.evento.common.performance;

import org.evento.common.messaging.bus.EventoServer;
public class RemotePerformanceService extends PerformanceService {

	private final EventoServer eventoServer;


	public RemotePerformanceService(EventoServer eventoServer,
									double performanceCaptureRate) {
		super(performanceCaptureRate);
		this.eventoServer = eventoServer;
	}


	@Override
	public void sendServiceTimeMetricMessage(PerformanceServiceTimeMessage message) throws Exception {
		eventoServer.send(message);
	}

	@Override
	public void sendInvocationMetricMessage(PerformanceInvocationsMessage message) throws Exception {
		eventoServer.send(message);
	}


}
