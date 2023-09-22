package org.evento.parser.model.payload;

import java.io.Serializable;

public class Payload implements Serializable {

	private String name;

	private String domain;

	public Payload(String name) {
		this.name = name;
	}

	public Payload() {
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}
}
