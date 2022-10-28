package org.eventrails.server.domain.repository;

import org.eventrails.server.domain.model.Bundle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BundleRepository extends JpaRepository<Bundle, String> {
	void deleteByName(String bundleDeploymentName);
}