package com.evento.application.client;

/**
 * Lifecycle state of a {@link BundleClient}. Higher-level than the underlying
 * {@link com.evento.transport.state.ConnectionState} of a single {@code Transport}
 * — the supervisor remains in {@code RECONNECTING} across many transport-level
 * disconnects without churning the public state.
 */
public enum BundleClientState {
    /** Built but never started. */
    INITIAL,
    /** A connect attempt is in progress (first time or after a disconnect). */
    CONNECTING,
    /** TCP up, Hello sent, awaiting Welcome. */
    HANDSHAKING,
    /** Welcome received; sending handler registrations + enable. */
    REGISTERING,
    /** Fully connected: bundle is in the server's available view, requests can flow. */
    READY,
    /** Transport dropped; supervisor is waiting/backing off before the next connect. */
    RECONNECTING,
    /** {@code stop()} requested; finishing any drains. */
    CLOSING,
    /** Terminal. */
    CLOSED;

    public boolean canSend() {
        return this == READY;
    }

    public boolean isTerminal() {
        return this == CLOSED;
    }

    public boolean isLive() {
        return this == CONNECTING || this == HANDSHAKING || this == REGISTERING
                || this == READY || this == RECONNECTING;
    }
}
