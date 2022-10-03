package org.eventrails.server.domain.repository;

import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.model.Ranch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandlerRepository extends JpaRepository<Handler, Ranch> {

	public void deleteAllByRanch_Name(String ranchName);

	public Handler findByHandledPayload_Name(String name);
}