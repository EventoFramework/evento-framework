package org.evento.common.performance;

import java.io.Serializable;

public class PerformanceServiceTimeMessage implements Serializable {

	private String bundle;
	private String component;
	private String action;
	private long start;
	private long end;

	public PerformanceServiceTimeMessage() {
	}

	public PerformanceServiceTimeMessage(String bundle, String component, String action, long start, long end) {
		this.bundle = bundle;
		this.component = component;
		this.action = action;
		this.start = start;
		this.end = end;
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


	public long getStart() {
		return start;
	}

	public void setStart(long start) {
		this.start = start;
	}

	public long getEnd() {
		return end;
	}

	public void setEnd(long end) {
		this.end = end;
	}

}
