package org.evento.parser.model.payload;

public class ServiceEvent extends Event{
	public ServiceEvent(String name) {
		super(name);
	}

	public ServiceEvent() {
		super();
	}

	@Override
	public String toString() {
		return getName();
	}
}
