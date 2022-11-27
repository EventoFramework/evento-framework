package org.evento.server.domain.repository;

import org.evento.server.domain.model.Payload;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayloadRepository extends JpaRepository<Payload, String> {
}