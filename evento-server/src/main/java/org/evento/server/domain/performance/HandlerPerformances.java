package org.evento.server.domain.performance;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

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
