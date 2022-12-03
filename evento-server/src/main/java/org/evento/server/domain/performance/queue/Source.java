package org.evento.server.domain.performance.queue;

import java.util.HashSet;
import java.util.Set;

public class Source extends Node{


	private String name;
	private Set<Node> target = new HashSet<>();

	public Source(long id, String name) {
		super(id);
		this.name = name;
	}

	public Source() {
	}

	public Set<Node> getTarget() {
		return target;
	}

	public void setTarget(Set<Node> target) {
		this.target = target;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
