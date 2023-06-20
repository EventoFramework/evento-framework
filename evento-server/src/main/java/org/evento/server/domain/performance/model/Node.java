package org.evento.server.domain.performance.model;

public abstract class Node {

	private long id;

	public Node(long id) {
		this.id = id;
	}

	public Node() {
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}
}
