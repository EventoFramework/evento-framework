package org.eventrails.parser.model.payload;

public class Payload {

	private final String name;

	public Payload(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}
}
