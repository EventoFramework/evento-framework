package org.evento.application.bus;

public class ClusterNodeAddress {
    private final String serverAddress;
    private final int serverPort;

    public ClusterNodeAddress(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public int getServerPort() {
        return serverPort;
    }
}
