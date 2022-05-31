package org.eventrails.parser.model.payload;

public class ServiceEvent extends Event{
	public ServiceEvent(String name) {
		super(name);
	}

	@Override
	public String toString() {
		return getName();
	}
}
