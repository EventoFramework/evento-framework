package org.evento.common.performance;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.modeling.messaging.message.internal.ClusterNodeIsBoredMessage;
import org.evento.common.modeling.messaging.message.internal.ClusterNodeIsSufferingMessage;

public abstract class AutoscalingProtocol {

	protected final Logger logger = LogManager.getLogger(ThreadCountAutoscalingProtocol.class);
	private final MessageBus messageBus;
	private final String bundleId;
	private final String serverName;

	protected AutoscalingProtocol(MessageBus messageBus, String bundleId, String serverName) {
		this.messageBus = messageBus;
		this.bundleId = bundleId;
		this.serverName = serverName;
	}

	public abstract void arrival();
	public abstract void departure();

	protected void sendBoredSignal() throws Exception {
		messageBus.cast(
				messageBus.findNodeAddress(serverName),
				new ClusterNodeIsBoredMessage(bundleId, messageBus.getAddress().getNodeId())
		);
		logger.info("ClusterNodeIsBoredMessage sent by timer");
	}

	protected void sendSufferingSignal() throws Exception {
		messageBus.cast(
				messageBus.findNodeAddress(serverName),
				new ClusterNodeIsSufferingMessage(bundleId)
		);
		logger.info("ClusterNodeIsSufferingMessage sent");

	}
}
