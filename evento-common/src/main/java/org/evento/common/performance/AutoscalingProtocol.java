package org.evento.common.performance;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.messaging.bus.EventoServer;
import org.evento.common.modeling.messaging.message.internal.ClusterNodeIsBoredMessage;

public abstract class AutoscalingProtocol {

	protected final Logger logger = LogManager.getLogger(ThreadCountAutoscalingProtocol.class);
	private final EventoServer eventoServer;

	protected AutoscalingProtocol(EventoServer eventoServer) {
		this.eventoServer = eventoServer;
	}

	public abstract void arrival();

	public abstract void departure();

	protected void sendBoredSignal() throws Exception {
		eventoServer.send(new ClusterNodeIsBoredMessage(eventoServer.getBundleId(), eventoServer.getInstanceId()));
		logger.info("ClusterNodeIsBoredMessage sent by timer");
	}

	protected void sendSufferingSignal() throws Exception {
		eventoServer.send(new ClusterNodeIsBoredMessage(eventoServer.getBundleId(), eventoServer.getInstanceId()));
		logger.info("ClusterNodeIsSufferingMessage sent");

	}
}
