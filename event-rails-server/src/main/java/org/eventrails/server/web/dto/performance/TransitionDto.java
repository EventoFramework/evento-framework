package org.eventrails.server.web.dto.performance;

import org.eventrails.server.service.performance.Post;
import org.eventrails.server.service.performance.Transition;

import java.util.Set;
import java.util.stream.Collectors;

public class TransitionDto {
	private long id;
	private String name;
	private Set<Long> target;
	public TransitionDto(Transition transition){
		id = transition.getId();
		name = transition.getName();
		target = transition.getTarget().stream().map(Post::getId).collect(Collectors.toSet());
	}

	public TransitionDto() {
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
}
