package com.evento.application.bus;

import lombok.Getter;

import java.util.List;

/**
 * Connection settings for the bundle's link to the evento-server cluster.
 *
 * <p>Reconnect / retry / disable backoff knobs live on the v2
 * {@code NettyTransportConfig} / {@code ReconnectStrategy} instead — this
 * type's job is just to carry the list of candidate addresses the
 * {@code BundleClient} can dial.
 */
@Getter
public class EventoServerMessageBusConfiguration {

    private final List<ClusterNodeAddress> addresses;

    public EventoServerMessageBusConfiguration(ClusterNodeAddress... addresses) {
        if (addresses.length < 1) {
            throw new IllegalArgumentException(
                    "Addresses must contain at least one address, no address specified for event bus configuration");
        }
        this.addresses = List.of(addresses);
    }
}
