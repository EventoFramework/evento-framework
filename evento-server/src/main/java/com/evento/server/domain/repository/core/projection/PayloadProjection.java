package com.evento.server.domain.repository.core.projection;

import java.time.Instant;

public interface PayloadProjection {
	String getName();

	String getDescription();

	String getDetail();

	String getDomain();

	String getType();

	String getJsonSchema();

	Boolean getValidJsonSchema();

	String getRegisteredId();

	Instant getUpdatedAt();

	String getSubscribers();

	String getInvokers();

	String getReturnedBy();

	String getUsedBy();

	String getPath();
	String getLinePrefix();
	Integer getLine();

	/** Id of the bundle the payload is registered in; used by the GUI to resolve the repository URL. */
	String getBundleId();

}
