package org.evento.server.domain.performance.queue;

import org.evento.server.domain.model.Payload;
import org.evento.server.domain.performance.modeling.PerformanceFetcher;

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

	public ServiceStation station(String bundleId, String componentName, String componentType, String action, String actionType, boolean async, Integer numServers) {
		var p = performanceFetcher.getMeanServiceTime(bundleId, componentName, action);
		var ss  = new ServiceStation(idGenerator.getAndIncrement(), bundleId, componentName, componentType, action, actionType, async, numServers, p);
		nodes.add(ss);
		return ss;
	}

	public ServiceStation station(String bundleId, String componentName, String componentType, String action, String actionType, boolean async, Integer numServers, Double p) {
		var ss  = new ServiceStation(idGenerator.getAndIncrement(), bundleId, componentName, componentType, action, actionType, async, numServers, p);
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


	public Source source(Payload handledPayload) {
		var source = new Source(idGenerator.getAndIncrement(), handledPayload.getName(), handledPayload.getType().toString());
		nodes.add(source);
		return source;
	}
}
