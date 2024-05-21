package com.evento.server.es.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SnapshotRepository extends JpaRepository<Snapshot, String> {

}
