package org.evento.parser.model;

import org.evento.parser.model.component.Component;
import org.evento.parser.model.payload.PayloadDescription;
import org.evento.parser.model.component.*;
import org.evento.parser.model.payload.*;

import java.io.Serializable;
import java.util.List;

public class BundleDescription implements Serializable {

	private List<Component> components;
	private List<PayloadDescription> payloadDescriptions;

	private String bundleId;
	private long bundleVersion;

	private boolean autorun;

	private int minInstances;

	private int maxInstances;

	public BundleDescription(String bundleId, long bundleVersion, boolean autorun,
							 int minInstances,
							 int maxInstances,
							 List<Component> components,
							 List<PayloadDescription> payloadDescriptions) {
		this.components = components;
		this.payloadDescriptions = payloadDescriptions;
		this.bundleId = bundleId;
		this.bundleVersion = bundleVersion;
		this.autorun = autorun;
		this.minInstances = minInstances;
		this.maxInstances = maxInstances;
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

	public boolean getAutorun() {
		return autorun;
	}

	public void setAutorun(boolean autorun) {
		this.autorun = autorun;
	}

	public int getMinInstances() {
		return minInstances;
	}

	public void setMinInstances(int minInstances) {
		this.minInstances = minInstances;
	}

	public int getMaxInstances() {
		return maxInstances;
	}

	public void setMaxInstances(int maxInstances) {
		this.maxInstances = maxInstances;
	}
}