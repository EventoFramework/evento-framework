package org.eventrails.server.domain.performance.modeling;

public interface PerformanceFetcher {
	Performance getPerformance(String bundle, String component, String action);
}
