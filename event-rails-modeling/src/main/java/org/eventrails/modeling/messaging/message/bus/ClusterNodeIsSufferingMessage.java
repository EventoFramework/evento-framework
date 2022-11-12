package org.eventrails.modeling.messaging.message.bus;

import java.io.Serializable;

public class ClusterNodeIsSufferingMessage implements Serializable {

	private String bundleName;

	public ClusterNodeIsSufferingMessage() {
	}

	public ClusterNodeIsSufferingMessage(String bundleName) {
		this.bundleName = bundleName;
	}

	public String getBundleName() {
		return bundleName;
	}

	public void setBundleName(String bundleName) {
		this.bundleName = bundleName;
	}
}
