package org.eventrails.parser.model;

import org.eventrails.parser.model.component.*;
import org.eventrails.parser.model.payload.*;

import java.io.Serializable;
import java.util.List;

public class BundleDescription implements Serializable {

	private List<Component> components;
	private List<PayloadDescription> payloadDescriptions;

	private String bundleId;
	private long bundleVersion;

	public BundleDescription(String bundleId, long bundleVersion, List<Component> components, List<PayloadDescription> payloadDescriptions) {
		this.components = components;
		this.payloadDescriptions = payloadDescriptions;
		this.bundleId = bundleId;
		this.bundleVersion = bundleVersion;
	}

	public BundleDescription() {
	}

	public List<Component> getComponents() {
		return components;
	}

	public List<PayloadDescription> getPayloadDescriptions() {
		return payloadDescriptions;
	}

	public String getBundleId() {
		return bundleId;
	}

	public long getBundleVersion() {
		return bundleVersion;
	}
}
