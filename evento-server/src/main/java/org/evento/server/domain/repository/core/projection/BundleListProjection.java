package org.evento.server.domain.repository.core.projection;

import org.evento.server.domain.model.core.BucketType;

public interface BundleListProjection {
	String getId();

	Integer getVersion();

	Boolean getAutorun();

	BucketType getBucketType();

	String getDescription();

	String getComponents();

	String getDomains();
}
