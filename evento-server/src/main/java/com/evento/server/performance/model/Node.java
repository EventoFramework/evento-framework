package com.evento.server.performance.model;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public abstract class Node {

	private long id;

	private String path;
	private String linePrefix;
	private List<Integer> lines;

	public Node(long id) {
		this.id = id;
	}

	public Node() {
	}

}
