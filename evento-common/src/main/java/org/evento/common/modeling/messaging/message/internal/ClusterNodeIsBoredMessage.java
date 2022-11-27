package org.evento.common.modeling.messaging.message.internal;

import java.io.Serializable;

public class ClusterNodeIsBoredMessage implements Serializable {

	private String bundleId;

	private String nodeId;

	public ClusterNodeIsBoredMessage() {
	}

	public ClusterNodeIsBoredMessage(String bundleId, String nodeId) {
		this.bundleId = bundleId;
		this.nodeId = nodeId;
	}

	public String getBundleId() {
		return bundleId;
	}

	public void setBundleId(String bundleId) {
		this.bundleId = bundleId;
	}

	public String getNodeId() {
		return nodeId;
	}

	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}
}
