package org.eventrails.parser.model;

import org.eventrails.parser.model.component.*;
import org.eventrails.parser.model.payload.*;

import java.util.HashSet;
import java.util.List;

public class Application {

	private final List<Component> components;
	private final List<PayloadDescription> payloadDescriptions;

	public Application(List<Component> components, List<PayloadDescription> payloadDescriptions) {
		this.components = components;
		this.payloadDescriptions = payloadDescriptions;
	}


	public List<Component> getComponents() {
		return components;
	}

	public List<PayloadDescription> getPayloadDescriptions() {
		return payloadDescriptions;
	}
}
