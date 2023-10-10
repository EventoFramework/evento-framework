package org.evento.server.es.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SnapshotRepository extends JpaRepository<Snapshot, String> {

    @Query("update Snapshot s set s.deletedAt = current_timestamp where s.aggregateId = ?1")
    @Modifying
    void deleteAggregate(String aggregateIdentifier);
}
