package org.evento.server.domain.performance.queue;

import java.util.HashSet;
import java.util.Set;

public class Source extends Node{


	private String name;
	private String type;
	private Set<Node> target = new HashSet<>();

	public Source(long id, String name, String type) {
		super(id);
		this.name = name;
		this.type = type;
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

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
}
