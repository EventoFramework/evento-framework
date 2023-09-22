package org.evento.parser.model;

import org.evento.parser.model.component.Component;
import org.evento.parser.model.payload.PayloadDescription;

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

	private String description;
	private String detail;

	public BundleDescription(String bundleId, long bundleVersion, boolean autorun,
							 int minInstances,
							 int maxInstances,
							 List<Component> components,
							 List<PayloadDescription> payloadDescriptions,
							 String description,
							 String detail) {
		this.components = components;
		this.payloadDescriptions = payloadDescriptions;
		this.bundleId = bundleId;
		this.bundleVersion = bundleVersion;
		this.autorun = autorun;
		this.minInstances = minInstances;
		this.maxInstances = maxInstances;
		this.description = description;
		this.detail = detail;
	}

	public BundleDescription() {
	}

	public List<Component> getComponents() {
		return components;
	}

	public void setComponents(List<Component> components) {
		this.components = components;
	}

	public List<PayloadDescription> getPayloadDescriptions() {
		return payloadDescriptions;
	}

	public void setPayloadDescriptions(List<PayloadDescription> payloadDescriptions) {
		this.payloadDescriptions = payloadDescriptions;
	}

	public String getBundleId() {
		return bundleId;
	}

	public void setBundleId(String bundleId) {
		this.bundleId = bundleId;
	}

	public long getBundleVersion() {
		return bundleVersion;
	}

	public void setBundleVersion(long bundleVersion) {
		this.bundleVersion = bundleVersion;
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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getDetail() {
		return detail;
	}

	public void setDetail(String detail) {
		this.detail = detail;
	}
}
