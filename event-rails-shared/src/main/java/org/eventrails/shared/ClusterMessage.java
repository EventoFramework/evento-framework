package org.eventrails.shared;

public class ClusterMessage {
	private String action;
	private String body;

	public ClusterMessage(String action, String body) {
		this.action = action;
		this.body = body;
	}

	public ClusterMessage() {
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}
}
