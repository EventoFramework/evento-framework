package org.evento.server.performance.modeling;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Setter
@Getter
public class Post {
	private long id;
	private String bundle;
	private String component;
	private String action;
	private Set<Transition> target;

	private long marking;

	public Post(long id, String bundle, String component, String action) {
		this(id, bundle, component, action, 0);
	}

	public Post(long id, String bundle, String component, String action, long initialMarking) {
		this.id = id;
		this.bundle = bundle;
		this.component = component;
		this.action = action;
		target = new HashSet<>();
		marking = initialMarking;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Post post = (Post) o;
		return id == post.id;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

}
