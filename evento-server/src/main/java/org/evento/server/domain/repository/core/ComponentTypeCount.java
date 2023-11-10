package org.evento.server.domain.repository.core;

import org.evento.common.modeling.bundle.types.ComponentType;

public interface ComponentTypeCount {
	ComponentType getType();

	Long getCount();
}
