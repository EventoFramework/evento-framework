package org.evento.server.domain.performance.queue;

import org.evento.server.service.performance.PerformanceStoreService;

import java.util.HashMap;

public class ServiceStation extends Node{

	private String bundle;
	private String component;
	private String componentType;
	private String action;
	private String actionType;

	private boolean async;
	private HashMap<Node, Double> target = new HashMap<>();
	private Integer numServers;

	private Double meanServiceTime;

	private String handlerId;

	public ServiceStation(long id, String bundle, String component, String componentType, String action,
						  String actionType,
						  boolean async,
						  Integer numServers, Double meanServiceTime,
						  String handlerId) {
		super(id);
		this.bundle = bundle;
		this.component = component;
		this.action = action;
		this.numServers = numServers;
		this.async = async;
		this.meanServiceTime = meanServiceTime;
		this.actionType = actionType;
		this.componentType = componentType;
		this.handlerId = handlerId;
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

	public HashMap<Node, Double> getTarget() {
		return target;
	}

	public void setTarget(HashMap<Node, Double> target) {
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

	public String getHandlerId() {
		return handlerId;
	}

	public void setHandlerId(String handlerId) {
		this.handlerId = handlerId;
	}

	public void addTarget(Node a, PerformanceStoreService performanceStoreService) {
		if(a instanceof ServiceStation ss){
			target.put(a, performanceStoreService.getInvocationProbability(bundle, component, action, ss.action));
		}else{
			target.put(a, null);
		}
	}
}
