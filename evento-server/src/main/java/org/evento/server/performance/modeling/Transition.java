package org.evento.server.performance.modeling;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Setter
public class Transition {

	@Getter
	private long id;
	@Getter
	private String bundle;
	@Getter
	private String component;
	@Getter
	private String action;
	@Getter
	private Set<Post> target;

	private boolean async;

	@Getter
	private Double meanServiceTime;


	public Transition(long id, String bundle, String component, String action, Double meanServiceTime) {
		this.id = id;
		this.bundle = bundle;
		this.component = component;
		this.action = action;
		target = new HashSet<>();
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

}
