package org.evento.server.domain.repository.projection;

public interface PayloadListProjection {
	String getName();

	String getType();

	String getDescription();

	String getDomain();

	String getSubscribers();

	Integer getSubscriptions();

	Integer getInvocations();

	Integer getReturnedBy();

}
