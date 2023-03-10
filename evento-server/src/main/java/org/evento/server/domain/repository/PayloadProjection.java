package org.evento.server.domain.repository;

public interface PayloadProjection {
	String getName();
	String getType();
	String getDescription();
	String getDomain();
	String getSubscribers();
	Integer getSubscriptions();
	Integer getInvocations();
	Integer getReturnedBy();

}
