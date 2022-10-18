package org.eventrails.modeling.messaging.message.bus;

import java.io.Serializable;

public class ClusterNodeStatusUpdateMessage implements Serializable {

	private boolean newStatus;

	public ClusterNodeStatusUpdateMessage() {
	}

	public ClusterNodeStatusUpdateMessage(boolean newStatus) {
		this.newStatus = newStatus;
	}

	public boolean getNewStatus() {
		return newStatus;
	}

	public void setNewStatus(boolean newStatus) {
		this.newStatus = newStatus;
	}
}
