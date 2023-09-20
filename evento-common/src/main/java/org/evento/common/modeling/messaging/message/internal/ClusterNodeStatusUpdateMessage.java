package org.evento.common.modeling.messaging.message.internal;

import java.io.Serializable;

public class ClusterNodeStatusUpdateMessage implements Serializable {

	private Boolean newStatus;

	public ClusterNodeStatusUpdateMessage() {
	}

	public ClusterNodeStatusUpdateMessage(boolean newStatus) {
		this.newStatus = newStatus;
	}

	public Boolean getNewStatus() {
		return newStatus;
	}

	public void setNewStatus(Boolean newStatus) {
		this.newStatus = newStatus;
	}
}
