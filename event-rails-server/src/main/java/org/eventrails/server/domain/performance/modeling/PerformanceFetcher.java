package org.eventrails.server.domain.performance.modeling;

public interface PerformanceFetcher {
	Double getMeanServiceTime(String bundle, String component, String action);
}
