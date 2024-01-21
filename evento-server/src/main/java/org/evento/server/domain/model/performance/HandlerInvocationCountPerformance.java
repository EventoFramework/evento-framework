package org.evento.server.domain.model.performance;

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
@Table(name = "performance__handler_invocation_count")
public class HandlerInvocationCountPerformance {

	@Id
	private String id;

	private int lastCount;

	private double meanProbability;

}