package com.evento.server.domain.repository.core;

import com.evento.common.modeling.bundle.types.ComponentType;

public interface ComponentTypeCount {
	ComponentType getType();

	Long getCount();
}
