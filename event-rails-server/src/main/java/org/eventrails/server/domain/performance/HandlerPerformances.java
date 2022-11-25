package org.eventrails.server.domain.performance;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.eventrails.server.domain.performance.modeling.Performance;
import org.eventrails.server.service.performance.Action;

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
    private String id;

    private String bundle;

    private String component;

    private String action;

    private double lastServiceTime;

    private double meanServiceTime;

}
