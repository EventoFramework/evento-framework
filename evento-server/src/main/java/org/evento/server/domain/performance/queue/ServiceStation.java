package org.evento.server.domain.performance.queue;

import java.util.HashSet;
import java.util.Set;

public class ServiceStation extends Node{

	private String bundle;
	private String component;
	private String componentType;
	private String action;
	private String actionType;

	private boolean async;
	private Set<Node> target = new HashSet<>();
	private Integer numServers;

	private Double meanServiceTime;

	public ServiceStation(long id, String bundle, String component, String componentType, String action,
						  String actionType,
						  boolean async,
						  Integer numServers, Double meanServiceTime) {
		super(id);
		this.bundle = bundle;
		this.component = component;
		this.action = action;
		this.numServers = numServers;
		this.async = async;
		this.meanServiceTime = meanServiceTime;
		this.actionType = actionType;
		this.componentType = componentType;
	}

	public ServiceStation() {

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

	public Set<Node> getTarget() {
		return target;
	}

	public void setTarget(Set<Node> target) {
		this.target = target;
	}

	public Integer getNumServers() {
		return numServers;
	}

	public void setNumServers(Integer numServers) {
		this.numServers = numServers;
	}

	public boolean getAsync() {
		return async;
	}

	public void setAsync(boolean async) {
		this.async = async;
	}


	public Double getMeanServiceTime() {
		return meanServiceTime;
	}

	public void setMeanServiceTime(Double meanServiceTime) {
		this.meanServiceTime = meanServiceTime;
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
