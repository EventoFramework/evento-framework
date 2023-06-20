package org.evento.server.domain.performance.model;

import org.evento.server.service.performance.PerformanceStoreService;

import java.util.HashMap;
import java.util.Map;

public class Source extends Node {


	private String bundleId;
	private String componentName;
	private String name;
	private String type;

	private String handlerId;

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

	public String getHandlerId() {
		return handlerId;
	}

	public void setHandlerId(String handlerId) {
		this.handlerId = handlerId;
	}
}
