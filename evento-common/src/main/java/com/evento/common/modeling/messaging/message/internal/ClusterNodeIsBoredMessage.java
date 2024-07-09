package com.evento.common.modeling.messaging.message.internal;

import java.io.Serializable;

/**
 * Class representing a ClusterNodeIsBoredMessage.
 */
public class ClusterNodeIsBoredMessage implements Serializable {

	private String bundleId;

	private String instanceId;

	/**
	 * Creates a new instance of the {@code ClusterNodeIsBoredMessage} class.
	 */
	public ClusterNodeIsBoredMessage() {
	}

	/**
	 * Constructs a new ClusterNodeIsBoredMessage with the specified bundle ID and node ID.
	 *
	 * @param bundleId the bundle ID of the message
	 * @param instanceId the node ID of the message
	 */
	public ClusterNodeIsBoredMessage(String bundleId, String instanceId) {
		this.bundleId = bundleId;
		this.instanceId = instanceId;
	}

	/**
	 * Retrieves the bundle ID of the ClusterNodeIsBoredMessage.
	 *
	 * @return the bundle ID of the ClusterNodeIsBoredMessage
	 */
	public String getBundleId() {
		return bundleId;
	}

	/**
	 * Sets the bundle ID of the ClusterNodeIsBoredMessage.
	 *
	 * @param bundleId the new bundle ID to be set
	 */
	public void setBundleId(String bundleId) {
		this.bundleId = bundleId;
	}

	/**
	 * Retrieves the node ID of the ClusterNodeIsBoredMessage.
	 *
	 * @return the node ID of the ClusterNodeIsBoredMessage
	 */
	public String getInstanceId() {
		return instanceId;
	}

	/**
	 * Sets the node ID of the ClusterNodeIsBoredMessage.
	 *
	 * @param instanceId the new node ID to be set
	 */
	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}
}
