package com.evento.parser.model;

import com.evento.parser.model.component.Component;
import com.evento.parser.model.payload.PayloadDescription;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * A class representing a bundle description.
 * <p>
 * A bundle description contains information about a software bundle, including its components, payload descriptions,
 * bundle ID, bundle version, description, and detail.
 * <p>
 * The bundle description can be serialized and deserialized.
 */
public class BundleDescription implements Serializable {

	private ArrayList<Component> components;
	private ArrayList<PayloadDescription> payloadDescriptions;

	private String bundleId;
	private long bundleVersion;

	private String description;
	private String detail;
	private String linePrefix;

	/**
	 * Constructs a new BundleDescription with the specified parameters.
	 *
	 * @param bundleId the unique identifier of the bundle
	 * @param bundleVersion the version number of the bundle
	 * @param components the list of components included in the bundle
	 * @param payloadDescriptions the list of payload descriptions included in the bundle
	 * @param description a brief description of the bundle
	 * @param detail detailed information about the bundle
	 * @param linePrefix a prefix for each line in the bundle
	 */
	public BundleDescription(String bundleId, long bundleVersion,
							 ArrayList<Component> components,
							 ArrayList<PayloadDescription> payloadDescriptions,
							 String description,
							 String detail,
							 String linePrefix) {
		this.components = components;
		this.payloadDescriptions = payloadDescriptions;
		this.bundleId = bundleId;
		this.bundleVersion = bundleVersion;
		this.description = description;
		this.detail = detail;
		this.linePrefix = linePrefix;
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
	public ArrayList<Component> getComponents() {
		return components;
	}

	/**
	 * Sets the list of components included in the bundle.
	 *
	 * @param components the list of components to be set
	 */
	public void setComponents(ArrayList<Component> components) {
		this.components = components;
	}

	/**
	 * Retrieves the list of payload descriptions included in the bundle.
	 *
	 * @return the list of payload descriptions included in the bundle
	 */
	public ArrayList<PayloadDescription> getPayloadDescriptions() {
		return payloadDescriptions;
	}

	/**
	 * Sets the list of payload descriptions included in the bundle.
	 *
	 * @param payloadDescriptions the list of payload descriptions to be set
	 */
	public void setPayloadDescriptions(ArrayList<PayloadDescription> payloadDescriptions) {
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

	/**
	 * Retrieves the prefix for each line in the bundle*/
	public String getLinePrefix() {
		return linePrefix;
	}

	/**
	 * Sets the prefix for each line in the bundle.
	 *
	 * @param linePrefix the prefix to be set for each line in the bundle
	 */
	public void setLinePrefix(String linePrefix) {
		this.linePrefix = linePrefix;
	}

}
