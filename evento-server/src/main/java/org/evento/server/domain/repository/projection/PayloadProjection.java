package org.evento.server.domain.repository.projection;

import java.time.Instant;
import java.util.Map;

public interface PayloadProjection {
	String getName();
	String getDescription();
	String getDetail();
	String getDomain();
	String getType();
	String getJsonSchema();
	String getRegisteredId();
	Instant getUpdatedAt();

	String getSubscribers();
	String getInvokers();
	String getReturnedBy();
	String getUsedBy();

}
