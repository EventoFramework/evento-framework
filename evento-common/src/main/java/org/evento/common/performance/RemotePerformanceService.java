package org.evento.common.performance;

import org.evento.common.messaging.bus.EventoServer;
/**
 * RemotePerformanceService is a subclass of PerformanceService that provides functionality for sending performance metrics to a remote server.
 * It extends and overrides methods from the PerformanceService class to send metrics using an instance of the EventoServer interface.
 */
public class RemotePerformanceService extends PerformanceService {

	private final EventoServer eventoServer;


	/**
	 * Constructs a new RemotePerformanceService object with the given EventoServer instance and performance capture rate.
	 *
	 * @param eventoServer            The EventoServer instance for sending performance metrics.
	 * @param performanceCaptureRate  The rate at which performance metrics are captured.
	 */
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
