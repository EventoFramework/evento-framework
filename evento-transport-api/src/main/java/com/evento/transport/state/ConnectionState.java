package com.evento.transport.state;

public enum ConnectionState {
    DISCONNECTED,
    CONNECTING,
    HANDSHAKING,
    CONNECTED,
    DEGRADED,
    CLOSING,
    CLOSED;

    public boolean canSend() {
        return this == CONNECTED || this == DEGRADED;
    }

    public boolean isTerminal() {
        return this == CLOSED;
    }

    public boolean isActive() {
        return this == CONNECTING || this == HANDSHAKING || this == CONNECTED || this == DEGRADED;
    }
}
