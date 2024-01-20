package org.evento.parser.model;

import org.evento.parser.model.component.Component;
import org.evento.parser.model.payload.PayloadDescription;

import java.io.Serializable;
import java.util.List;

/**
 * A class representing a bundle description.
 *
 * A bundle description contains information about a software bundle, including its components, payload descriptions,
 * bundle ID, bundle version, autorun status, minimum and maximum instances, description, and detail.
 *
 * The bundle description can be serialized and deserialized.
 */
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

	/**
	 * Creates a new BundleDescription object with the specified parameters.
	 *
	 * @param bundleId            the ID of the bundle
	 * @param bundleVersion       the version of the bundle
	 * @param autorun             whether the bundle should autorun
	 * @param minInstances        the minimum number of instances allowed for the bundle
	 * @param maxInstances        the maximum number of instances allowed for the bundle
	 * @param components          the list of components included in the bundle
	 * @param payloadDescriptions the list of payload descriptions included in the bundle
	 * @param description         the description of the bundle
	 * @param detail              the detailed information about the bundle
	 */
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

	/**
	 * The BundleDescription class represents a description of a bundle.
	 */
	public BundleDescription() {
	}

	/**
	 * Retrieves the list of components included in the bundle.
	 *
	 * @return the list of components included in the bundle
	 */
	public List<Component> getComponents() {
		return components;
	}

	/**
	 * Sets the list of components included in the bundle.
	 *
	 * @param components the list of components to be set
	 */
	public void setComponents(List<Component> components) {
		this.components = components;
	}

	/**
	 * Retrieves the list of payload descriptions included in the bundle.
	 *
	 * @return the list of payload descriptions included in the bundle
	 */
	public List<PayloadDescription> getPayloadDescriptions() {
		return payloadDescriptions;
	}

	/**
	 * Sets the list of payload descriptions included in the bundle.
	 *
	 * @param payloadDescriptions the list of payload descriptions to be set
	 */
	public void setPayloadDescriptions(List<PayloadDescription> payloadDescriptions) {
		this.payloadDescriptions = payloadDescriptions;
	}

	/**
	 * Retrieves the bundle ID of the BundleDescription object.
	 *
	 * @return the bundle ID
	 */
	public String getBundleId() {
		return bundleId;
	}

	/**
	 * Sets the bundle ID of the BundleDescription object.
	 *
	 * @param bundleId the bundle ID to be set
	 */
	public void setBundleId(String bundleId) {
		this.bundleId = bundleId;
	}

	/**
	 * Retrieves the version of the bundle.
	 *
	 * @return the version of the bundle
	 */
	public long getBundleVersion() {
		return bundleVersion;
	}

	/**
	 * Sets the version of the bundle.
	 *
	 * @param bundleVersion the version of the bundle to be set
	 */
	public void setBundleVersion(long bundleVersion) {
		this.bundleVersion = bundleVersion;
	}

	/**
	 * Retrieves the value of the autorun attribute.
	 *
	 * @return true if the bundle should autorun, false otherwise
	 */
	public boolean getAutorun() {
		return autorun;
	}

	/**
	 * Sets the autorun attribute for the BundleDescription object.
	 *
	 * @param autorun whether the bundle should autorun
	 */
	public void setAutorun(boolean autorun) {
		this.autorun = autorun;
	}

	/**
	 * Retrieves the minimum number of instances allowed for the bundle.
	 *
	 * @return the minimum number of instances allowed for the bundle
	 */
	public int getMinInstances() {
		return minInstances;
	}

	/**
	 * Sets the minimum number of instances allowed for the bundle.
	 *
	 * @param minInstances the minimum number of instances to be set
	 */
	public void setMinInstances(int minInstances) {
		this.minInstances = minInstances;
	}

	/**
	 * Retrieves the maximum number of instances allowed for the bundle.
	 *
	 * @return the maximum number of instances allowed for the bundle
	 */
	public int getMaxInstances() {
		return maxInstances;
	}

	/**
	 * Sets the maximum number of instances allowed for the bundle.
	 *
	 * @param maxInstances the maximum number of instances to be set
	 */
	public void setMaxInstances(int maxInstances) {
		this.maxInstances = maxInstances;
	}

	/**
	 * Retrieves the description of the bundle.
	 *
	 * @return the description of the bundle
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Sets the description of the BundleDescription.
	 *
	 * @param description the new description for the BundleDescription
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Retrieves the detailed information about the bundle.
	 *
	 * @return the detailed information about the bundle
	 */
	public String getDetail() {
		return detail;
	}

	/**
	 * Sets the detailed information about the bundle.
	 *
	 * @param detail the detailed information about the bundle
	 */
	public void setDetail(String detail) {
		this.detail = detail;
	}
}
