package org.evento.server.domain.repository.projection;

public interface ComponentListProjection {
	String getComponentName();

	String getComponentType();

	String getDescription();

	String getDomains();

	String getBundleId();

	Integer getHandledMessages();

	Integer getProducedMessages();

	Integer getInvocations();
}
