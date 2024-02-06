package com.evento.server.domain.model.performance;

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
@Table(name = "performance__handler_invocation_count")
public class HandlerInvocationCountPerformance {

	@Id
	private String id;

	private int lastCount;

	private double meanProbability;

}
