package com.evento.server.bus.v2.router;

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

    public Phase phase() { return phase.get(); }
    public void transitionTo(Phase next) { phase.set(next); }
}
