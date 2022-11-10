package org.eventrails.server.web.dto.performance;

import org.eventrails.server.domain.performance.modeling.Post;
import org.eventrails.server.domain.performance.modeling.Transition;

import java.util.Set;
import java.util.stream.Collectors;

public class PostDto {

	private long id;
	private String bundle;
	private String component;
	private String action;
	private Set<Long> target;
	private long marking;
	public PostDto(Post post){
		id = post.getId();
		bundle = post.getBundle();
		component = post.getComponent();
		action = post.getAction();
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
}
