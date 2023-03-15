package org.evento.server.web.dto.performance;

import org.evento.server.domain.performance.queue.Node;
import org.evento.server.domain.performance.queue.ServiceStation;
import org.evento.server.domain.performance.queue.Source;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class NodeDTO implements Serializable {

	private long id;
	private String bundle;
	private String component;
	private String componentType;
	private String action;
	private String actionType;

	private boolean async;
	private Map<Long, Double> target = new HashMap<>();
	private Integer numServers;
	private String type;

	private String name;
	private Double meanServiceTime;

	public NodeDTO(Node node) {
		this.id = node.getId();
		this.type = node.getClass().getSimpleName();
		if (node instanceof ServiceStation s)
		{
			this.bundle = s.getBundle();
			this.component = s.getComponent();
			this.action = s.getAction();
			this.async = s.getAsync();
			this.actionType = s.getActionType();
			this.target = new HashMap<>();
			s.getTarget().forEach((k,v) -> {
				this.target.put(k.getId(), v);
			});
			this.numServers = s.getNumServers();
			this.meanServiceTime = s.getMeanServiceTime();
			this.componentType = s.getComponentType();
		} else if (node instanceof Source s)
		{
			this.target = new HashMap<>();
			s.getTarget().forEach((k,v) -> {
				this.target.put(k.getId(), v);
			});
			this.name = s.getName();
			this.action = s.getName();
			this.actionType = s.getType();
		}
	}

	public NodeDTO() {
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getBundle() {
		return bundle;
	}

	public void setBundle(String bundle) {
		this.bundle = bundle;
	}

	public String getComponent() {
		return component;
	}

	public void setComponent(String component) {
		this.component = component;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public boolean getAsync() {
		return async;
	}

	public void setAsync(boolean async) {
		this.async = async;
	}

	public Map<Long, Double> getTarget() {
		return target;
	}

	public void setTarget(Map<Long, Double> target) {
		this.target = target;
	}

	public Integer getNumServers() {
		return numServers;
	}

	public void setNumServers(Integer numServers) {
		this.numServers = numServers;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}


	public Double getMeanServiceTime() {
		return meanServiceTime;
	}

	public void setMeanServiceTime(Double meanServiceTime) {
		this.meanServiceTime = meanServiceTime;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getActionType() {
		return actionType;
	}

	public void setActionType(String actionType) {
		this.actionType = actionType;
	}

	public String getComponentType() {
		return componentType;
	}

	public void setComponentType(String componentType) {
		this.componentType = componentType;
	}
}
