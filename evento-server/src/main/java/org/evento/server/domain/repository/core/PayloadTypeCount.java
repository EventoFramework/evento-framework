package org.evento.server.domain.repository.core;

import org.evento.common.modeling.bundle.types.PayloadType;

public interface PayloadTypeCount {
	PayloadType getType();

	Long getCount();
}