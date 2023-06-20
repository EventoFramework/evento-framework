package org.evento.common.performance;

import java.io.Serializable;
import java.util.HashMap;

public class PerformanceInvocationsMessage implements Serializable {

	private String bundle;
	private String component;
	private String action;
	private HashMap<String, Integer> invocations;


	public PerformanceInvocationsMessage() {
	}

	public PerformanceInvocationsMessage(String bundle, String component, String action, HashMap<String, Integer> invocations) {
		this.bundle = bundle;
		this.component = component;
		this.action = action;
		this.invocations = invocations;
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

	public HashMap<String, Integer> getInvocations() {
		return invocations;
	}

	public void setInvocations(HashMap<String, Integer> invocations) {
		this.invocations = invocations;
	}
}
