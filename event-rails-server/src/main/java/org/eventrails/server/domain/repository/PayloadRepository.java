package org.eventrails.server.domain.repository;

import org.eventrails.server.domain.model.Payload;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayloadRepository extends JpaRepository<Payload, String> {
}