package com.evento.server.bus;

import org.jetbrains.annotations.NotNull;

public record NodeAddress(String bundleId, long bundleVersion, String instanceId) implements Comparable<NodeAddress> {


	@Override
	public String toString() {
		return bundleId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof NodeAddress that)) return false;

		return instanceId() != null ? instanceId().equals(that.instanceId()) : that.instanceId() == null;
	}

	@Override
	public int hashCode() {
		return instanceId() != null ? instanceId().hashCode() : 0;
	}

	@Override
	public int compareTo(@NotNull NodeAddress o) {
		return instanceId().compareTo(o.instanceId());
	}
}
