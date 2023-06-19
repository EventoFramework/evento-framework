package org.evento.server.domain.performance.model;

import org.evento.server.domain.model.Handler;
import org.evento.server.domain.performance.modeling.PerformanceFetcher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class PerformanceModel {

	private final PerformanceFetcher performanceFetcher;

	private final List<ServiceStation> sources = new ArrayList<>();

	private final List<Node> nodes = new ArrayList<>();

	private final AtomicLong idGenerator = new AtomicLong();

	public PerformanceModel(PerformanceFetcher performanceFetcher) {
		this.performanceFetcher = performanceFetcher;
	}

	public ServiceStation station(String bundleId, String componentName, String componentType,
								  String action, String actionType, boolean async, Integer numServers, String handlerId) {
		var p = performanceFetcher.getMeanServiceTime(bundleId, componentName, action);
		var ss = new ServiceStation(idGenerator.getAndIncrement(), bundleId, componentName,
				componentType, action, actionType, async, numServers, p, handlerId);
		nodes.add(ss);
		return ss;
	}

	public ServiceStation station(String bundleId, String componentName, String componentType, String action, String actionType, boolean async, Integer numServers, Double p, String handlerId) {
		var ss = new ServiceStation(idGenerator.getAndIncrement(), bundleId, componentName, componentType, action, actionType, async, numServers, p, handlerId);
		nodes.add(ss);
		return ss;
	}

	public Sink sink() {
		var sink = new Sink(idGenerator.getAndIncrement());
		nodes.add(sink);
		return sink;
	}

	public void addSource(ServiceStation s) {
		sources.add(s);
	}

	public List<Node> getNodes() {
		return nodes;
	}


	public Source source(Handler handler) {
		var source = new Source(idGenerator.getAndIncrement(), handler.getComponent().getBundle().getId(), handler.getComponent().getComponentName(), handler.getHandledPayload().getName(), handler.getHandledPayload().getType().toString(), handler.getUuid());
		nodes.add(source);
		return source;
	}

	public Source source(String payloadName, String payloadType) {
		var source = new Source(idGenerator.getAndIncrement(), "server", "server", payloadName, payloadType, "server_" + payloadName);
		nodes.add(source);
		return source;
	}
}
