package com.evento.server.bus.router;

import com.evento.server.bus.NodeAddress;
import com.evento.transport.Transport;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Server-side bookkeeping for one connected bundle. Carries the resolved
 * {@link NodeAddress} (set after a successful handshake), the underlying
 * {@link Transport}, and the connection token that ties this session to its
 * registry entry. Mutable because the address becomes known mid-lifecycle:
 * accept → handshake (address known) → registration → enable.
 */
public final class BundleSession {

    public enum Phase { ACCEPTED, REGISTERED, READY, CLOSED }

    private final Transport transport;
    private final String connectionToken;
    private final Instant acceptedAt;
    private final AtomicReference<NodeAddress> address = new AtomicReference<>();
    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.ACCEPTED);

    public BundleSession(Transport transport, String connectionToken) {
        this.transport = transport;
        this.connectionToken = connectionToken;
        this.acceptedAt = Instant.now();
    }

    public Transport transport() { return transport; }
    public String connectionToken() { return connectionToken; }
    public Instant acceptedAt() { return acceptedAt; }

    public NodeAddress address() { return address.get(); }

    public void bindAddress(NodeAddress a) {
        if (!address.compareAndSet(null, a)) {
            throw new IllegalStateException("address already bound: " + address.get());
        }
    }

    /** The capabilities negotiated with this peer in the handshake (none before it). */
    private volatile java.util.Set<String> capabilities = java.util.Set.of();

    public java.util.Set<String> capabilities() { return capabilities; }

    public void setCapabilities(java.util.Set<String> negotiated) {
        this.capabilities = negotiated == null ? java.util.Set.of() : java.util.Set.copyOf(negotiated);
    }

    /** Whether this peer negotiated streamed replies (it can decode stream frames). */
    public boolean supportsStreams() {
        return capabilities.contains(com.evento.transport.HandshakeProtocol.CAPABILITY_STREAM);
    }

    public Phase phase() { return phase.get(); }
    public void transitionTo(Phase next) { phase.set(next); }

    private volatile boolean framesReceived;

    /** Record that the peer sent a frame above the heartbeat layer (a Hello, at least). */
    public void markFrameReceived() {
        if (!framesReceived) framesReceived = true;
    }

    /**
     * Whether the peer sent anything but heartbeats. False for a connection that
     * only pinged — a bundle's fail-back probe — which is logged at DEBUG.
     */
    public boolean framesReceived() { return framesReceived; }

    /** Set by the transport's disconnect, before anything else looks at the session. */
    private volatile boolean disconnected;
    /** The registered session's cleanup runs once, whichever of disconnect and Hello sees it last. */
    private final java.util.concurrent.atomic.AtomicBoolean cleanedUp = new java.util.concurrent.atomic.AtomicBoolean();

    public void markDisconnected() { disconnected = true; }
    public boolean disconnected() { return disconnected; }

    /** True for the one caller that should clean up the registered session. */
    public boolean claimCleanup() { return cleanedUp.compareAndSet(false, true); }
}
