package com.evento.application.bus;

import lombok.Getter;

import java.util.List;

/**
 * Connection settings for the bundle's link to the evento-server cluster.
 *
 * <p>{@code addresses} is an ordered failover list: the bundle attaches to the
 * first broker that answers (and passes the identity checks), most preferred
 * first — normally the broker of the bundle's own region/cell, then the others.
 * After a session that was up drops, dialing restarts from the first address.
 * While attached to another address the bundle probes the first one and moves
 * back once it has been healthy for a stability window (fail-back, see
 * {@code EventoBundle.Builder#setFailbackStabilityWindow}).
 *
 * <p>Reconnect / retry / disable backoff knobs live on the v2
 * {@code NettyTransportConfig} / {@code ReconnectStrategy} instead.
 */
@Getter
public class EventoServerMessageBusConfiguration {

    private final List<ClusterNodeAddress> addresses;

    /**
     * The instance id the broker must announce, or null to accept any. See
     * {@link com.evento.application.client.BundleClientConfig#expectedServerInstanceId()}
     * for why a bundle that dials a name should also check who answered.
     *
     * <p>With more than one address this refuses every broker but one, so the
     * failover list is useless; use {@link #expectedClusterName} instead.
     */
    private final String expectedServerInstanceId;

    /**
     * The cluster name the broker must announce ({@code evento.cluster.name}
     * on the server), or null to accept any. The identity check that fits an
     * address list: all brokers of the cluster share it.
     */
    private final String expectedClusterName;

    public EventoServerMessageBusConfiguration(ClusterNodeAddress... addresses) {
        this(null, null, addresses);
    }

    private EventoServerMessageBusConfiguration(String expectedServerInstanceId,
                                                String expectedClusterName,
                                                ClusterNodeAddress... addresses) {
        if (addresses.length < 1) {
            throw new IllegalArgumentException(
                    "Addresses must contain at least one address, no address specified for event bus configuration");
        }
        this.addresses = List.of(addresses);
        this.expectedServerInstanceId = blankToNull(expectedServerInstanceId);
        this.expectedClusterName = blankToNull(expectedClusterName);
    }

    /** The same addresses, plus the broker identity they must answer with. */
    public EventoServerMessageBusConfiguration withExpectedServerInstanceId(String expectedServerInstanceId) {
        return new EventoServerMessageBusConfiguration(expectedServerInstanceId, expectedClusterName,
                addresses.toArray(new ClusterNodeAddress[0]));
    }

    /** The same addresses, plus the cluster name every one of them must announce. */
    public EventoServerMessageBusConfiguration withExpectedClusterName(String expectedClusterName) {
        return new EventoServerMessageBusConfiguration(expectedServerInstanceId, expectedClusterName,
                addresses.toArray(new ClusterNodeAddress[0]));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
