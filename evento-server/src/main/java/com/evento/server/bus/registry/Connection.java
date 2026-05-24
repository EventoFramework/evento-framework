package com.evento.server.bus.registry;

import com.evento.server.bus.NodeAddress;
import com.evento.transport.Transport;

import java.time.Instant;

/**
 * One registered bundle connection: the {@link NodeAddress} that identifies it,
 * the underlying {@link Transport}, the connection token (a fresh UUID per
 * accept used to defeat "ghost leaves" from a superseded prior socket), and
 * the join timestamp for observability.
 *
 * <p>Immutable record. The registry replaces entries atomically rather than
 * mutating them, so listeners never see a half-updated state.
 */
public record Connection(
        NodeAddress address,
        Transport transport,
        String connectionToken,
        Instant joinedAt
) {}
