package org.evento.server.bus;

import org.jetbrains.annotations.NotNull;

public class NodeAddress implements Comparable<NodeAddress> {

	private final String bundleId;
	private final long bundleVersion;
	private final String instanceId;

	public NodeAddress(String bundleId, long bundleVersion, String instanceId) {
		this.bundleId = bundleId;
		this.bundleVersion = bundleVersion;
		this.instanceId = instanceId;
	}

	public String getBundleId() {
		return bundleId;
	}

	public long getBundleVersion() {
		return bundleVersion;
	}

	public String getInstanceId() {
		return instanceId;
	}

	@Override
	public String toString() {
		return bundleId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof NodeAddress that)) return false;

		return getInstanceId() != null ? getInstanceId().equals(that.getInstanceId()) : that.getInstanceId() == null;
	}

	@Override
	public int hashCode() {
		return getInstanceId() != null ? getInstanceId().hashCode() : 0;
	}

	@Override
	public int compareTo(@NotNull NodeAddress o) {
		return getInstanceId().compareTo(o.getInstanceId());
	}
}
