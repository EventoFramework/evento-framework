package org.evento.common.performance;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.messaging.bus.EventoServer;
import org.evento.common.modeling.messaging.message.internal.ClusterNodeIsBoredMessage;

/**
 * The AutoscalingProtocol class is an abstract class that provides a template for autoscaling protocols in a cluster system.
 */
public abstract class AutoscalingProtocol {

	protected static final Logger logger = LogManager.getLogger(ThreadCountAutoscalingProtocol.class);
	private final EventoServer eventoServer;

	/**
	 * The AutoscalingProtocol class is an abstract class that provides a template for autoscaling protocols in a cluster system.
	 */
	protected AutoscalingProtocol(EventoServer eventoServer) {
		this.eventoServer = eventoServer;
	}

	/**
	 * This method is called to handle the arrival of a request or message.
	 * The behavior of this method should be implemented in the subclass.
	 */
	public abstract void arrival();

	/**
	 * This method is called to handle the departure of a request or message.
	 * The behavior of this method should be implemented in the subclass.
	 */
	public abstract void departure();

	/**
	 * Sends a bored signal to the cluster.
	 * Throws an exception if the message sending fails.
	 *
	 * @throws Exception if the message sending fails
	 */
	protected void sendBoredSignal() throws Exception {
		eventoServer.send(new ClusterNodeIsBoredMessage(eventoServer.getBundleId(), eventoServer.getInstanceId()));
		logger.info("ClusterNodeIsBoredMessage sent by timer");
	}

	/**
	 * Sends a suffering signal to the cluster indicating that the node is suffering.
	 * Throws an exception if the message sending fails.
	 *
	 * @throws Exception if the message sending fails
	 */
	protected void sendSufferingSignal() throws Exception {
		eventoServer.send(new ClusterNodeIsBoredMessage(eventoServer.getBundleId(), eventoServer.getInstanceId()));
		logger.info("ClusterNodeIsSufferingMessage sent");

	}
}
