package org.evento.server.performance.modeling;

public interface PerformanceFetcher {
	Double getMeanServiceTime(String bundle, String component, String action);
}
