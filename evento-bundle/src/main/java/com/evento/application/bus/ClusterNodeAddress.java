package com.evento.application.bus;

/**
 * Represents the address of a cluster node, including the server address and port.
 * This is a record, a special type in Java for immutable data-holding classes.
 */
public record ClusterNodeAddress(String serverAddress, int serverPort, EventoSocketConfig socketConfig) {

    public ClusterNodeAddress(String serverAddress, int serverPort) {
        this(serverAddress, serverPort, new EventoSocketConfig());
    }
}