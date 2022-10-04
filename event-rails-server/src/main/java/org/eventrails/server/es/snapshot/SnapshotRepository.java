package org.eventrails.server.es.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SnapshotRepository extends JpaRepository<Snapshot, String> {
}
