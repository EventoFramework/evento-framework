package org.evento.server.domain.repository;

import org.evento.server.domain.model.Bundle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BundleRepository extends JpaRepository<Bundle, String> {
}