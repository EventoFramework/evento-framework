package com.evento.application.client.stream;

import io.netty.handler.ssl.SslContext;

import java.time.Duration;
import java.util.Objects;

/**
 * Where a bundle offers its streamed answers on direct connections (3.0), bypassing the
 * broker for the data: the broker still routes the query (load balancing, locality,
 * deadlines, authentication of both bundles), then hands the caller a one-time ticket to
 * this endpoint.
 *
 * <p>For deployments where callers can reach the answering bundle directly (same network,
 * a Kubernetes service per pod, ...) and the broker should carry no bulk data at all.
 * Callers that cannot connect fall back to the broker on their own, so a wrong
 * {@code advertisedHost} costs a connection attempt, not a failed query.
 *
 * @param port             the port to listen on; 0 picks a free one
 * @param advertisedHost   the host callers connect to (this bundle's address as they see it)
 * @param advertisedPort   the port callers connect to; 0 = the port listened on (no NAT)
 * @param serverSslContext TLS for the endpoint (a server context), or null for plaintext;
 *                         callers use the TLS settings of their own broker connection
 * @param ticketTtl        how long a ticket may wait for its caller to connect
 */
public record DirectStreamConfig(int port, String advertisedHost, int advertisedPort,
                                 SslContext serverSslContext, Duration ticketTtl) {

    public DirectStreamConfig {
        Objects.requireNonNull(advertisedHost, "advertisedHost");
        if (advertisedHost.isBlank()) throw new IllegalArgumentException("advertisedHost must not be blank");
        if (port < 0 || port > 65535) throw new IllegalArgumentException("port out of range: " + port);
        if (advertisedPort < 0 || advertisedPort > 65535) {
            throw new IllegalArgumentException("advertisedPort out of range: " + advertisedPort);
        }
        if (ticketTtl == null || ticketTtl.isZero() || ticketTtl.isNegative()) ticketTtl = Duration.ofSeconds(10);
    }

    /** Plaintext on {@code port}, advertised as {@code advertisedHost:port}, 10 s tickets. */
    public static DirectStreamConfig on(int port, String advertisedHost) {
        return new DirectStreamConfig(port, advertisedHost, 0, null, Duration.ofSeconds(10));
    }

    public DirectStreamConfig withTls(SslContext serverSslContext) {
        return new DirectStreamConfig(port, advertisedHost, advertisedPort, serverSslContext, ticketTtl);
    }

    public DirectStreamConfig withAdvertisedPort(int advertisedPort) {
        return new DirectStreamConfig(port, advertisedHost, advertisedPort, serverSslContext, ticketTtl);
    }

    public DirectStreamConfig withTicketTtl(Duration ticketTtl) {
        return new DirectStreamConfig(port, advertisedHost, advertisedPort, serverSslContext, ticketTtl);
    }
}
