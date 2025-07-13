package com.evento.common.modeling.messaging.message.internal.discovery;

import com.evento.common.modeling.exceptions.ExceptionWrapper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * The BundleRegistration class represents the registration information for a bundle in a system.
 */
public class BundleRegistered implements Serializable {

	private String bundleId;
	private long bundleVersion;
	private String instanceId;

	private String serverInstance;

	private boolean isRegistered;
	private ExceptionWrapper exception;


	/**
	 * Creates a new instance of BundleRegistration with the specified parameters.
	 *
	 * @param bundleId           the bundle ID assigned to the bundle
	 * @param bundleVersion      the version of the bundle
	 * @param instanceId         the unique instance ID of the bundle
	 */
	public BundleRegistered(
			String bundleId,
			long bundleVersion,
			String instanceId,
			String serverInstance,
			boolean isRegistered,
			ExceptionWrapper exception
			) {
		this.bundleId = bundleId;
		this.bundleVersion = bundleVersion;
		this.instanceId = instanceId;
		this.serverInstance = serverInstance;
		this.isRegistered = isRegistered;
		this.exception = exception;
	}

	/**
	 * The BundleRegistration class represents the registration information for a bundle in a system.
	 */
	public BundleRegistered() {
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
	 * Retrieves the server instance associated with the bundle.
	 *
	 * @return the server instance as a string
	 */
	public String getServerInstance() {
		return serverInstance;
	}

	/**
	 * Sets the server instance associated with the bundle.
	 *
	 * @param serverInstance the server instance to associate with the bundle
	 */
	public void setServerInstance(String serverInstance) {
		this.serverInstance = serverInstance;
	}

	/**
	 * Indicates whether the bundle is registered in the system.
	 *
	 * @return true if the bundle is registered, false otherwise
	 */
	public boolean isRegistered() {
		return isRegistered;
	}

	/**
	 * Sets the registration status of the bundle.
	 *
	 * @param registered the new registration status to set, where {@code true} indicates
	 *                   that the bundle is registered, and {@code false} indicates it is not.
	 */
	public void setRegistered(boolean registered) {
		isRegistered = registered;
	}

	/**
	 * Retrieves the ExceptionWrapper object associated with the current instance.
	 *
	 * @return the ExceptionWrapper object representing the exception
	 */
	public ExceptionWrapper getException() {
		return exception;
	}

	/**
	 * Sets the ExceptionWrapper object associated with the current instance.
	 *
	 * @param exception the ExceptionWrapper object representing the exception
	 */
	public void setException(ExceptionWrapper exception) {
		this.exception = exception;
	}
}
