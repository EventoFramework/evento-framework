package com.evento.application.client.v2.handshake;

import com.evento.application.client.v2.BundleClientConfig;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.message.Hello;

import java.util.UUID;

/**
 * Builds a fresh {@link Hello} for each handshake attempt. Pulls bundle id /
 * instance id / version / token / capabilities from the
 * {@link BundleClientConfig} so the supervisor doesn't have to know the
 * wire-level field layout.
 */
public final class HelloFactory {

    private HelloFactory() {}

    public static Hello build(BundleClientConfig config) {
        return new Hello(
                UUID.randomUUID(),
                HandshakeProtocol.PROTOCOL_VERSION,
                config.bundleId(),
                config.instanceId(),
                config.bundleVersion(),
                config.capabilities(),
                config.authToken(),
                System.currentTimeMillis()
        );
    }
}
