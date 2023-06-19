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
