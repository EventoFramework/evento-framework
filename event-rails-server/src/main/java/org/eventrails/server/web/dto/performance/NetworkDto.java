package org.eventrails.server.web.dto.performance;

import org.eventrails.server.domain.performance.modeling.Network;

import java.util.List;
import java.util.stream.Collectors;

public class NetworkDto {
	private List<PostDto> posts;
	private List<TransitionDto> transitions;

	public NetworkDto(Network network) {
		posts = network.getPosts().stream().map(PostDto::new).collect(Collectors.toList());
		transitions = network.getTransitions().stream().map(TransitionDto::new).collect(Collectors.toList());
	}

	public NetworkDto() {
	}

	public List<PostDto> getPosts() {
		return posts;
	}

	public void setPosts(List<PostDto> posts) {
		this.posts = posts;
	}

	public List<TransitionDto> getTransitions() {
		return transitions;
	}

	public void setTransitions(List<TransitionDto> transitions) {
		this.transitions = transitions;
	}
}
