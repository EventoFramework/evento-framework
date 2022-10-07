package org.eventrails.modeling.gateway;

import java.io.Serializable;

public class ServerHandlerResponse implements Serializable {

	private int status;
	private String body;

	public ServerHandlerResponse() {
	}

	public ServerHandlerResponse(int status, String body) {
		this.status = status;
		this.body = body;
	}

	public int getStatus() {
		return status;
	}

	public String getBody() {
		return body;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public void setBody(String body) {
		this.body = body;
	}
}
