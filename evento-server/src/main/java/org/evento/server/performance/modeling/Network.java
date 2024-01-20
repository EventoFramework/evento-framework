package org.evento.server.performance.modeling;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Network {
	@Getter
	private final List<Post> posts = new ArrayList<>();
	@Getter
	private final List<Post> sources = new ArrayList<>();
	@Getter
	private final List<Transition> transitions = new ArrayList<>();
	private final AtomicLong idGenerator = new AtomicLong();

	private final HashMap<String, Post> instancesPosts = new HashMap<>();

	private final PerformanceFetcher performanceFetcher;

	public Network(PerformanceFetcher performanceFetcher) {
		this.performanceFetcher = performanceFetcher;
	}


	public Post post(String bundle, String component, String action) {
		return post(bundle, component, action, 0);
	}

	public Post post(String bundle, String component, String action, long initialMarking) {
		var p = new Post(idGenerator.getAndIncrement(), bundle, component, action, initialMarking);
		posts.add(p);
		return p;
	}

	public Transition transition(String bundle, String component, String action, boolean async) {
		var p = performanceFetcher.getMeanServiceTime(bundle, component, action);
		var t = new Transition(idGenerator.getAndIncrement(), bundle, component, action, p);
		transitions.add(t);
		var iq = instancesPosts.get(bundle);
		iq.getTarget().add(t);
		t.getTarget().add(iq);
		t.setAsync(async);
		return t;
	}

	public void addSource(Post post) {
		sources.add(post);
	}

	public Post instancePost(String bundle) {
		var instancePost = post(bundle, "Instance", "Token", 1);
		instancesPosts.put(bundle, instancePost);
		return instancePost;
	}
}
