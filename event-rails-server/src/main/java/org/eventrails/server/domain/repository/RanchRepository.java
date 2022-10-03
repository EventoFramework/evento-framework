package org.eventrails.server.domain.repository;

import org.eventrails.server.domain.model.Ranch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RanchRepository extends JpaRepository<Ranch, String> {
	void deleteByName(String ranchDeploymentName);
}