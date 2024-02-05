package org.evento.server.domain.model.performance;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "performance__handler_service_time")
public class HandlerServiceTimePerformance {

	@Id
	private String id;

	private double agedMeanServiceTime;
	private long lastServiceTime;
	private long maxServiceTime;
	private long minServiceTime;

	private double agedMeanArrivalInterval;
	private long lastArrivalInterval;
	private long maxArrivalInterval;
	private long minArrivalInterval;

	private long lastArrival;
	private long count;

}
