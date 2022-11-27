package org.evento.server.domain.repository;

import org.evento.server.domain.model.EventConsumerState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComponentEventConsumingStateRepository extends JpaRepository<EventConsumerState, String> {
}