package com.evento.server.domain.repository.core;

import com.evento.common.modeling.bundle.types.PayloadType;

public interface PayloadTypeCount {
	PayloadType getType();

	Long getCount();
}
