package org.evento.common.modeling.messaging.message.bus;

public abstract class NodeAddress implements Comparable<NodeAddress> {

	private final Object address;
	private final String bundleId;
	private final long bundleVersion;
	private final String nodeId;

	public NodeAddress(String bundleId, long bundleVersion, Object address, String nodeId) {
		this.bundleId = bundleId;
		this.bundleVersion = bundleVersion;
		this.address = address;
		this.nodeId = nodeId;
	}

	public <T> T getAddress() {
		return (T) address;
	}

	public String getBundleId() {
		return bundleId;
	}

	public String getNodeId() {
		return nodeId;
	}

	public long getBundleVersion() {
		return bundleVersion;
	}

	@Override
	public String toString() {
		return bundleId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof NodeAddress that)) return false;

		return getNodeId() != null ? getNodeId().equals(that.getNodeId()) : that.getNodeId() == null;
	}

	@Override
	public int hashCode() {
		return getNodeId() != null ? getNodeId().hashCode() : 0;
	}
}
