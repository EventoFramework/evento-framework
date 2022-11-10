package org.eventrails.server.domain.performance;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "performance__handler")
public class HandlerPerformances {

    @Id
    private String handlerId;

    private double lastServiceTime;

    private double meanServiceTime;

    private Double lastThroughput;

    private Instant lastThroughputUpdatedAt;

    private int departures;

    private Instant updatedAt;

    private Instant createdAt;
}
