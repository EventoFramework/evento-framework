package com.evento.application.client;

import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.transport.netty.NettyTransportConfig;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Settings for one {@link BundleClient} instance. Built by a {@link Builder}
 * to keep callers from juggling 10+ constructor arguments.
 *
 * <p>{@code host} / {@code port} identify the server. {@code bundleId} +
 * {@code instanceId} identify this bundle (the server's
 * {@code ConnectionRegistry} keys on {@code instanceId}).
 *
 * <p>{@code handlerPayloadTypes} drives what gets registered with the server
 * on {@code Hello} success — the bundle is only made available for the listed
 * payload types. {@code authToken} is included in {@code Hello} when set;
 * the server validates via its {@code TokenValidator}.
 *
 * <p>{@code transportConfig} is the {@link NettyTransportConfig} used to open
 * the connection (Netty event loops, codec, heartbeat tuning, optional TLS).
 * Defaults are sensible but every field is overridable.
 */
public record BundleClientConfig(
        String host,
        int port,
        String bundleId,
        String instanceId,
        String bundleVersion,
        String authToken,
        List<String> handlerPayloadTypes,
        List<RegisteredHandler> registeredHandlers,
        Map<String, String[]> payloadInfo,
        Set<String> capabilities,
        Duration handshakeTimeout,
        Duration registrationTimeout,
        Duration defaultRequestTimeout,
        Duration shutdownDeadline,
        NettyTransportConfig transportConfig,
        boolean autoEnable
) {

    public BundleClientConfig {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(bundleId, "bundleId");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(bundleVersion, "bundleVersion");
        Objects.requireNonNull(transportConfig, "transportConfig");
        handlerPayloadTypes = handlerPayloadTypes == null ? List.of() : List.copyOf(handlerPayloadTypes);
        registeredHandlers = registeredHandlers == null ? List.of() : List.copyOf(registeredHandlers);
        payloadInfo = payloadInfo == null ? Map.of() : Map.copyOf(payloadInfo);
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        handshakeTimeout = handshakeTimeout == null ? Duration.ofSeconds(5) : handshakeTimeout;
        registrationTimeout = registrationTimeout == null ? Duration.ofSeconds(5) : registrationTimeout;
        defaultRequestTimeout = defaultRequestTimeout == null ? Duration.ofSeconds(30) : defaultRequestTimeout;
        shutdownDeadline = shutdownDeadline == null ? Duration.ofSeconds(10) : shutdownDeadline;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String host;
        private int port;
        private String bundleId;
        private String instanceId;
        private String bundleVersion = "1";
        private String authToken;
        private List<String> handlerPayloadTypes = List.of();
        private List<RegisteredHandler> registeredHandlers = List.of();
        private Map<String, String[]> payloadInfo = Map.of();
        private Set<String> capabilities = Set.of();
        private Duration handshakeTimeout = Duration.ofSeconds(5);
        private Duration registrationTimeout = Duration.ofSeconds(5);
        private Duration defaultRequestTimeout = Duration.ofSeconds(30);
        private Duration shutdownDeadline = Duration.ofSeconds(10);
        private NettyTransportConfig transportConfig = NettyTransportConfig.defaults();
        private boolean autoEnable = true;

        public Builder host(String host) { this.host = host; return this; }
        public Builder port(int port) { this.port = port; return this; }
        public Builder bundleId(String id) { this.bundleId = id; return this; }
        public Builder instanceId(String id) { this.instanceId = id; return this; }
        public Builder bundleVersion(String v) { this.bundleVersion = v; return this; }
        public Builder authToken(String token) { this.authToken = token; return this; }
        public Builder handlerPayloadTypes(List<String> types) { this.handlerPayloadTypes = types; return this; }
        public Builder registeredHandlers(List<RegisteredHandler> handlers) { this.registeredHandlers = handlers; return this; }
        public Builder payloadInfo(Map<String, String[]> info) { this.payloadInfo = info; return this; }
        public Builder capabilities(Set<String> caps) { this.capabilities = caps; return this; }
        public Builder handshakeTimeout(Duration d) { this.handshakeTimeout = d; return this; }
        public Builder registrationTimeout(Duration d) { this.registrationTimeout = d; return this; }
        public Builder defaultRequestTimeout(Duration d) { this.defaultRequestTimeout = d; return this; }
        public Builder shutdownDeadline(Duration d) { this.shutdownDeadline = d; return this; }
        public Builder transportConfig(NettyTransportConfig c) { this.transportConfig = c; return this; }
        public Builder autoEnable(boolean v) { this.autoEnable = v; return this; }

        public BundleClientConfig build() {
            return new BundleClientConfig(host, port, bundleId, instanceId, bundleVersion,
                    authToken, handlerPayloadTypes, registeredHandlers, payloadInfo, capabilities,
                    handshakeTimeout, registrationTimeout, defaultRequestTimeout, shutdownDeadline,
                    transportConfig, autoEnable);
        }
    }
}
