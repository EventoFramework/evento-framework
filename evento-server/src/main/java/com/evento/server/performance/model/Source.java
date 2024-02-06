package com.evento.server.performance.model;

import lombok.Getter;
import lombok.Setter;
import com.evento.server.service.performance.PerformanceStoreService;

import java.util.HashMap;
import java.util.Map;

public class Source extends Node implements HasTarget {


	private String bundleId;
	private String componentName;
	@Setter
	@Getter
	private String name;
	@Setter
	@Getter
	private String type;

	@Setter
	@Getter
	private String handlerId;

	@Setter
	@Getter
	private Map<Node, Double> target = new HashMap<>();

	public Source(long id, String bundleId, String componentName, String name, String type, String handlerId) {
		super(id);
		this.name = name;
		this.type = type;
		this.bundleId = bundleId;
		this.componentName = componentName;
		this.handlerId = handlerId;
	}

	public Source() {
	}

	@Override
	public void addTarget(ActionNode s, PerformanceStoreService performanceStoreService) {
		target.put(s, performanceStoreService.getInvocationProbability(
				bundleId,
				componentName,
				name,
				s.getAction()
		));

	}

}
