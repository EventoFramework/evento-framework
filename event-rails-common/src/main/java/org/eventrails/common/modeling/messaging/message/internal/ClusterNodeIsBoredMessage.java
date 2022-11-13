package org.eventrails.common.modeling.messaging.message.internal;

import java.io.Serializable;

public class ClusterNodeIsBoredMessage implements Serializable {

	private String bundleName;

	private String nodeId;

	public ClusterNodeIsBoredMessage() {
	}

	public ClusterNodeIsBoredMessage(String bundleName, String nodeId) {
		this.bundleName = bundleName;
		this.nodeId = nodeId;
	}

	public String getBundleName() {
		return bundleName;
	}

	public void setBundleName(String bundleName) {
		this.bundleName = bundleName;
	}

	public String getNodeId() {
		return nodeId;
	}

	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}
}
