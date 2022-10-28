package org.eventrails.modeling.bundle;

public class BundleHandlerResponse {
	private int status;
	private String body;

	public BundleHandlerResponse() {
	}

	public BundleHandlerResponse(int status, String body) {
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
