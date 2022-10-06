package org.eventrails.modeling.ranch;

public class RanchHandlerResponse {
	private int status;
	private String body;

	public RanchHandlerResponse() {
	}

	public RanchHandlerResponse(int status, String body) {
		this.status = status;
		this.body = body;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}
}
