package org.evento.common.modeling.messaging.message.internal.discovery;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * The BundleRegistration class represents the registration information for a bundle in a system.
 */
public class BundleRegistration implements Serializable {

	private String bundleId;
	private long bundleVersion;
	private String instanceId;
	private ArrayList<RegisteredHandler> registeredHandlers;

	private HashMap<String, String[]> payloadInfo;


	/**
	 * Creates a new instance of BundleRegistration with the specified parameters.
	 *
	 * @param bundleId           the bundle ID assigned to the bundle
	 * @param bundleVersion      the version of the bundle
	 * @param instanceId         the unique instance ID of the bundle
	 * @param registeredHandlers the list of RegisteredHandlers registered with the bundle
	 * @param payloadInfo        the payload information associated with the bundle
	 */
	public BundleRegistration(
			String bundleId,
			long bundleVersion,
			String instanceId,
			ArrayList<RegisteredHandler> registeredHandlers,
			HashMap<String, String[]> payloadInfo
			) {
		this.bundleId = bundleId;
		this.registeredHandlers = registeredHandlers;
		this.bundleVersion = bundleVersion;
		this.payloadInfo = payloadInfo;
		this.instanceId = instanceId;
	}

	/**
	 * The BundleRegistration class represents the registration information for a bundle in a system.
	 */
	public BundleRegistration() {
	}

	/**
	 * Returns the bundle ID of the BundleRegistration.
	 *
	 * @return the bundle ID
	 */
	public String getBundleId() {
		return bundleId;
	}

	/**
	 * Sets the bundle ID of the BundleRegistration.
	 *
	 * @param bundleId the bundle ID assigned to the bundle
	 */
	public void setBundleId(String bundleId) {
		this.bundleId = bundleId;
	}

	/**
	 * Retrieves the list of RegisteredHandlers associated with this BundleRegistration.
	 *
	 * @return the list of RegisteredHandlers
	 */
	public ArrayList<RegisteredHandler> getHandlers() {
		return registeredHandlers;
	}

	/**
	 * Sets the list of RegisteredHandlers associated with this BundleRegistration.
	 *
	 * @param registeredHandlers the list of RegisteredHandlers to be set
	 */
	public void setHandlers(ArrayList<RegisteredHandler> registeredHandlers) {
		this.registeredHandlers = registeredHandlers;
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
	 * Sets the version of the bundle in the BundleRegistration.
	 *
	 * @param bundleVersion the version of the bundle
	 */
	public void setBundleVersion(long bundleVersion) {
		this.bundleVersion = bundleVersion;
	}


	/**
	 * Retrieves the payload information associated with the BundleRegistration.
	 *
	 * @return the payload information as a HashMap where the key is a payload ID and the value is an array of strings
	 * representing the JSON schema and domain of the payload.
	 */
	public HashMap<String, String[]> getPayloadInfo() {
		return payloadInfo;
	}

	/**
	 * Sets the payload information associated with the BundleRegistration.
	 *
	 * @param payloadInfo the payload information as a HashMap where the key is a payload ID and the value is an array of strings
	 *                    representing the JSON schema and domain of the payload
	 */
	public void setPayloadInfo(HashMap<String, String[]> payloadInfo) {
		this.payloadInfo = payloadInfo;
	}

	/**
	 * Retrieves the unique instance ID associated with the BundleRegistration.
	 *
	 * @return the instance ID
	 */
	public String getInstanceId() {
		return instanceId;
	}

	/**
	 * Sets the instance ID for the BundleRegistration.
	 *
	 * @param instanceId the unique instance ID of the bundle
	 */
	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}

	/**
	 * Retrieves the list of RegisteredHandlers that are registered with the BundleRegistration.
	 *
	 * @return the list of RegisteredHandlers registered with the BundleRegistration
	 */
	public ArrayList<RegisteredHandler> getRegisteredHandlers() {
		return registeredHandlers;
	}

	/**
	 * Sets the list of RegisteredHandlers associated with this BundleRegistration.
	 *
	 * @param registeredHandlers the list of RegisteredHandlers to be set
	 */
	public void setRegisteredHandlers(ArrayList<RegisteredHandler> registeredHandlers) {
		this.registeredHandlers = registeredHandlers;
	}
}
