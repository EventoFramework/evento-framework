package org.evento.parser.model.payload;

import java.io.Serializable;

public class Payload implements Serializable {

	private String name;

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

	@Override
	public int hashCode() {
		return name.hashCode();
	}
}
