package org.eventrails.server.domain.repository;

import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.model.NanoService;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandlerRepository extends JpaRepository<Handler, NanoService> {
}