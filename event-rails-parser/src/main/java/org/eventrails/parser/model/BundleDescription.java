package org.eventrails.parser.model;

import org.eventrails.parser.model.component.*;
import org.eventrails.parser.model.payload.*;

import java.io.Serializable;
import java.util.List;

public class BundleDescription implements Serializable {

	private List<Component> components;
	private List<PayloadDescription> payloadDescriptions;

	public BundleDescription(List<Component> components, List<PayloadDescription> payloadDescriptions) {
		this.components = components;
		this.payloadDescriptions = payloadDescriptions;
	}

	public BundleDescription() {
	}

	public List<Component> getComponents() {
		return components;
	}

	public List<PayloadDescription> getPayloadDescriptions() {
		return payloadDescriptions;
	}
}
