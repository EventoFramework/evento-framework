package org.eventrails.server.web.dto.performance;

import org.eventrails.server.domain.performance.modeling.Post;
import org.eventrails.server.domain.performance.modeling.Transition;

import java.util.Set;
import java.util.stream.Collectors;

public class TransitionDto {
	private long id;
	private String bundle;
	private String component;
	private String action;
	private Set<Long> target;

	private Double meanServiceTime;

	private Double meanThroughput;
	public TransitionDto(Transition transition){
		id = transition.getId();
		bundle = transition.getBundle();
		component = transition.getComponent();
		action = transition.getAction();
		target = transition.getTarget().stream().map(Post::getId).collect(Collectors.toSet());
		meanThroughput = transition.getMeanThroughput();
		meanServiceTime = transition.getMeanServiceTime();
	}

	public TransitionDto() {
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

	public Double getMeanServiceTime() {
		return meanServiceTime;
	}

	public void setMeanServiceTime(Double meanServiceTime) {
		this.meanServiceTime = meanServiceTime;
	}

	public Double getMeanThroughput() {
		return meanThroughput;
	}

	public void setMeanThroughput(Double meanThroughput) {
		this.meanThroughput = meanThroughput;
	}
}
