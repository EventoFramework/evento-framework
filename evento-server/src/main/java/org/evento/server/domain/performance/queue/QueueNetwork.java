package org.evento.server.domain.performance.queue;

import org.evento.server.domain.performance.modeling.PerformanceFetcher;
import org.evento.server.domain.performance.modeling.Post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class QueueNetwork {

	private final PerformanceFetcher performanceFetcher;

	private final List<ServiceStation> sources = new ArrayList<>();

	private final List<Node> nodes = new ArrayList<>();

	private final AtomicLong idGenerator = new AtomicLong();

	public QueueNetwork(PerformanceFetcher performanceFetcher) {
		this.performanceFetcher = performanceFetcher;
	}

	public ServiceStation ss(String bundleId, String componentName, String action, boolean async, Integer numServers) {
		var p = performanceFetcher.getMeanServiceTime(bundleId, componentName, action);
		var ss  = new ServiceStation(idGenerator.getAndIncrement(), bundleId, componentName, action, async, numServers, p);
		nodes.add(ss);
		return ss;
	}

	public Sink sink() {
		var sink  = new Sink(idGenerator.getAndIncrement());
		nodes.add(sink);
		return sink;
	}

	public void addSource(ServiceStation s) {
		sources.add(s);
	}

	public List<Node> getNodes() {
		return nodes;
	}
}
