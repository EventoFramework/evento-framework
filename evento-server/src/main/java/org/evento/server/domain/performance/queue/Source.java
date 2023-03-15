package org.evento.server.domain.performance.queue;

import org.evento.server.service.performance.PerformanceStoreService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Source extends Node{


	private String bundleId;
	private String componentName;
	private String name;
	private String type;


	private Map<Node, Double> target = new HashMap<>();

	public Source(long id, String bundleId, String componentName, String name, String type) {
		super(id);
		this.name = name;
		this.type = type;
		this.bundleId = bundleId;
		this.componentName = componentName;
	}

	public Source() {
	}

	public Map<Node, Double> getTarget() {
		return target;
	}

	public void setTarget(Map<Node, Double> target) {
		this.target = target;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public void addTarget(ServiceStation s, PerformanceStoreService performanceStoreService) {
		target.put(s, performanceStoreService.getInvocationProbability(
				bundleId,
				componentName,
				name,
				s.getAction()
		));

	}
}
