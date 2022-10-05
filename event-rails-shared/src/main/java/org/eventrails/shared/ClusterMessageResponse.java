package org.eventrails.shared;

public class ClusterMessageResponse {
	private int status;
	private String body;

	public ClusterMessageResponse() {
	}

	public ClusterMessageResponse(int status, String body) {
		this.status = status;
		this.body = body;
	}

	public int getStatus() {
		return status;
	}

	public String getBody() {
		return body;
	}
}


