package org.eventrails.server.service.performance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Network {
	private final List<Post> posts = new ArrayList<>();
	private final List<Post> sources = new ArrayList<>();
	private final List<Transition> transitions = new ArrayList<>();
	private final AtomicLong idGenerator = new AtomicLong();


	public Post post(String name){
		return post(name, 0);
	}

	public Post post(String name, long initialMarking){
		var p = new Post(idGenerator.getAndIncrement(), name, initialMarking);
		posts.add(p);
		return p;
	}

	public List<Post> getPosts() {
		return posts;
	}

	public Transition transition(String name){
		var t = new Transition(idGenerator.getAndIncrement(), name);
		transitions.add(t);
		return t;
	}

	public List<Transition> getTransitions() {
		return transitions;
	}

	public void addSource(Post post) {
		sources.add(post);
	}

	public List<Post> getSources() {
		return sources;
	}

}
