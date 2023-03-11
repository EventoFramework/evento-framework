package org.evento.server.domain.repository.projection;

import org.evento.server.domain.model.BucketType;

public interface BundleListProjection {
	String getId();
	Integer getVersion();
	Boolean getAutorun();

	BucketType getBucketType();
	String getDescription();
	String getComponents();
	String getDomains();
}
