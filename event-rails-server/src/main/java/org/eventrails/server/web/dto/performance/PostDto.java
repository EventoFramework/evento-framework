package org.eventrails.server.web.dto.performance;

import org.eventrails.server.service.performance.Post;
import org.eventrails.server.service.performance.Transition;

import java.util.Set;
import java.util.stream.Collectors;

public class PostDto {

	private long id;
	private String name;
	private Set<Long> target;
	private long marking;
	public PostDto(Post post){
		id = post.getId();
		name = post.getName();
		marking = post.getMarking();
		target = post.getTarget().stream().map(Transition::getId).collect(Collectors.toSet());
	}

	public PostDto() {
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Set<Long> getTarget() {
		return target;
	}

	public void setTarget(Set<Long> target) {
		this.target = target;
	}

	public long getMarking() {
		return marking;
	}

	public void setMarking(long marking) {
		this.marking = marking;
	}
}
