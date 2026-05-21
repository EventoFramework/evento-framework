package com.evento.application.bus;

/**
 * Address of a cluster node — host + port that {@code BundleClient} dials
 * to reach an evento-server instance.
 */
public record ClusterNodeAddress(String serverAddress, int serverPort) {
}
