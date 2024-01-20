package org.evento.common.modeling.messaging.message.internal;

import java.io.Serializable;

/**
 * Represents a message indicating that a cluster node is suffering.
 */
public class ClusterNodeIsSufferingMessage implements Serializable {

	private String bundleId;

	/**
	 * Creates a new instance of {@code ClusterNodeIsSufferingMessage}.
	 * This constructor initializes an empty message indicating that a cluster node is suffering.
	 */
	public ClusterNodeIsSufferingMessage() {
	}

	/**
	 * Creates a new instance of {@code ClusterNodeIsSufferingMessage} with the specified bundleId.
	 *
	 * @param bundleId the bundleId of the suffering cluster node
	 */
	public ClusterNodeIsSufferingMessage(String bundleId) {
		this.bundleId = bundleId;
	}

	/**
	 * Retrieves the bundle ID associated with this message.
	 *
	 * @return The bundle ID of the message
	 */
	public String getBundleId() {
		return bundleId;
	}

	/**
	 * Sets the bundle ID associated with this message.
	 *
	 * @param bundleId the bundle ID to be set
	 */
	public void setBundleId(String bundleId) {
		this.bundleId = bundleId;
	}
}
