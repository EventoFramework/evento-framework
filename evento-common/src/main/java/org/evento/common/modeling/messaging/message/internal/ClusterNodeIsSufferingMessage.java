package org.evento.common.modeling.messaging.message.internal;

import java.io.Serializable;

public class ClusterNodeIsSufferingMessage implements Serializable {

	private String bundleId;

	public ClusterNodeIsSufferingMessage() {
	}

	public ClusterNodeIsSufferingMessage(String bundleId) {
		this.bundleId = bundleId;
	}

	public String getBundleId() {
		return bundleId;
	}

	public void setBundleId(String bundleId) {
		this.bundleId = bundleId;
	}
}
