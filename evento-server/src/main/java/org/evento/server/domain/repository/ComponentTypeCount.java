package org.evento.server.domain.repository;

import org.evento.common.modeling.bundle.types.ComponentType;

public interface ComponentTypeCount {
    ComponentType getType();
    Long getCount();
}
