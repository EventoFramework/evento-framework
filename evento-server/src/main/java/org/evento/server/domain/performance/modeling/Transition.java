package org.evento.server.domain.performance.modeling;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Transition {

	private long id;
	private String bundle;
	private String component;
	private String action;
	private Set<Post> target;

	private boolean async;

	private Double meanServiceTime;


	public Transition(long id, String bundle, String component, String action, Double meanServiceTime) {
		this.id = id;
		this.bundle = bundle;
		this.component = component;
		this.action = action;
		target = new HashSet<>();
		this.meanServiceTime = meanServiceTime;
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

	public Set<Post> getTarget() {
		return target;
	}

	public void setTarget(Set<Post> target) {
		this.target = target;
	}

	public Double getMeanServiceTime() {
		return meanServiceTime;
	}

	public void setMeanServiceTime(Double meanServiceTime) {
		this.meanServiceTime = meanServiceTime;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Transition that = (Transition) o;
		return id == that.id;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	public boolean getAsync() {
		return async;
	}

	public void setAsync(boolean async) {
		this.async = async;
	}
}
