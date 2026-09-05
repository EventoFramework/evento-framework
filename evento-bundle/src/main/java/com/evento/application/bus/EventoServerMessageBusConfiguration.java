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

    /**
     * The instance id the broker must announce, or null to accept any. See
     * {@link com.evento.application.client.BundleClientConfig#expectedServerInstanceId()}
     * for why a bundle that dials a name should also check who answered.
     */
    private final String expectedServerInstanceId;

    public EventoServerMessageBusConfiguration(ClusterNodeAddress... addresses) {
        this(null, addresses);
    }

    private EventoServerMessageBusConfiguration(String expectedServerInstanceId, ClusterNodeAddress... addresses) {
        if (addresses.length < 1) {
            throw new IllegalArgumentException(
                    "Addresses must contain at least one address, no address specified for event bus configuration");
        }
        this.addresses = List.of(addresses);
        this.expectedServerInstanceId = expectedServerInstanceId == null || expectedServerInstanceId.isBlank()
                ? null : expectedServerInstanceId;
    }

    /** The same addresses, plus the broker identity they must answer with. */
    public EventoServerMessageBusConfiguration withExpectedServerInstanceId(String expectedServerInstanceId) {
        return new EventoServerMessageBusConfiguration(expectedServerInstanceId,
                addresses.toArray(new ClusterNodeAddress[0]));
    }
}
