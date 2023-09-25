package org.evento.server.performance.model;
import java.util.List;

public abstract class Node {

	private long id;

	private String path;
	private List<Integer> lines;

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

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public List<Integer> getLines() {
		return lines;
	}

	public void setLines(List<Integer> lines) {
		this.lines = lines;
	}
}
