package com.evento.spring.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code evento.bundle.*}: the bundle the starter runs. Only {@code id} is required; the bundle is
 * not configured at all without it.
 *
 * <pre>
 * evento.bundle.id=orders
 * evento.bundle.servers=evento-eu-1:3030,evento-eu-2:3030
 * evento.bundle.auth-token=${EVENTO_BUNDLE_AUTH_TOKEN}
 * evento.bundle.tls.enabled=true
 * evento.bundle.tls.trust-certs=/etc/evento/ca.pem
 * evento.bundle.serialization.restrict=true
 * </pre>
 */
@ConfigurationProperties(prefix = "evento.bundle")
public class EventoProperties {

    /** The bundle id the server routes to and the console shows. */
    private String id;

    /** This instance's id; a random UUID when unset. */
    private String instanceId;

    /** The bundle version. */
    private long version = 1;

    /**
     * The package scanned for Evento components (sub-packages included). Defaults to the package of
     * the {@code @SpringBootApplication} class.
     */
    private String basePackage;

    /** What the console shows for the bundle. */
    private String description;

    /** Server addresses, {@code host:port}; the first is the preferred one, the rest are fail-over targets. */
    private List<String> servers = new ArrayList<>(List.of("localhost:3030"));

    /** The shared secret of a server with {@code evento.server.bus.auth-token} set. */
    private String authToken;

    /** Refuse servers that announce another cluster name ({@code evento.cluster.name}). */
    private String expectedClusterName;

    /** This instance's zone, for {@code locality-preferred} routing. */
    private String zone;

    /** Timeout of gateway calls without an explicit one. */
    private Duration defaultRequestTimeout = Duration.ofSeconds(30);

    /** Start the bundle with the application context; false leaves it to {@link EventoBundleLifecycle#start()}. */
    private boolean autoStartup = true;

    /** How long a graceful stop waits for the consumer engines. */
    private Duration shutdownTimeout = Duration.ofSeconds(30);

    /** Readiness follows the connection to the server (REFUSING_TRAFFIC while it reconnects). */
    private boolean connectionReadiness;

    /** How often newly measured payload types are added to the Micrometer meters. */
    private Duration metricsRefresh = Duration.ofSeconds(15);

    private final Tls tls = new Tls();
    private final Serialization serialization = new Serialization();
    private final ConsumerState consumerState = new ConsumerState();
    private final Tracing tracing = new Tracing();

    /** TLS on the link to the server ({@code evento.server.bus.tls.*} on the server side). */
    public static class Tls {
        /** Connect with TLS. */
        private boolean enabled;
        /** PEM file with the CAs that sign the server certificate; the JDK trust store when unset. */
        private String trustCerts;
        /** PEM file with this bundle's certificate chain, for mutual TLS. */
        private String certChain;
        /** PEM file with this bundle's private key (PKCS#8), for mutual TLS. */
        private String privateKey;
        /** Password of an encrypted private key. */
        private String privateKeyPassword;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getTrustCerts() { return trustCerts; }
        public void setTrustCerts(String trustCerts) { this.trustCerts = trustCerts; }
        public String getCertChain() { return certChain; }
        public void setCertChain(String certChain) { this.certChain = certChain; }
        public String getPrivateKey() { return privateKey; }
        public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
        public String getPrivateKeyPassword() { return privateKeyPassword; }
        public void setPrivateKeyPassword(String privateKeyPassword) { this.privateKeyPassword = privateKeyPassword; }
    }

    /** Polymorphic payload deserialization (see {@code PayloadTypeAllowlist}). */
    public static class Serialization {
        /** Accept only the framework, JDK value types, the base package and {@code allowed-packages}. */
        private boolean restrict;
        /** More packages whose classes travel as payloads, e.g. a shared API module. */
        private List<String> allowedPackages = new ArrayList<>();

        public boolean isRestrict() { return restrict; }
        public void setRestrict(boolean restrict) { this.restrict = restrict; }
        public List<String> getAllowedPackages() { return allowedPackages; }
        public void setAllowedPackages(List<String> allowedPackages) { this.allowedPackages = allowedPackages; }
    }

    /** Where projectors, sagas and observers keep their checkpoints. */
    public static class ConsumerState {
        /** {@code auto}: JDBC when the JDBC store is on the classpath and there is a DataSource, else memory. */
        private Store store = Store.AUTO;
        /** The SQL dialect of the JDBC store. */
        private Dialect dialect = Dialect.POSTGRES;

        public Store getStore() { return store; }
        public void setStore(Store store) { this.store = store; }
        public Dialect getDialect() { return dialect; }
        public void setDialect(Dialect dialect) { this.dialect = dialect; }

        public enum Store { AUTO, MEMORY, JDBC }

        public enum Dialect { POSTGRES, MYSQL }
    }

    /** OpenTelemetry tracing, when {@code evento-opentelemetry} and an {@code OpenTelemetry} bean are present. */
    public static class Tracing {
        /** Trace handlers with the application's OpenTelemetry. */
        private boolean enabled = true;
        /** Event consumers start a new trace linked to the producer instead of joining its trace. */
        private boolean linkConsumers;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isLinkConsumers() { return linkConsumers; }
        public void setLinkConsumers(boolean linkConsumers) { this.linkConsumers = linkConsumers; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public String getBasePackage() { return basePackage; }
    public void setBasePackage(String basePackage) { this.basePackage = basePackage; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getServers() { return servers; }
    public void setServers(List<String> servers) { this.servers = servers; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public String getExpectedClusterName() { return expectedClusterName; }
    public void setExpectedClusterName(String expectedClusterName) { this.expectedClusterName = expectedClusterName; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public Duration getDefaultRequestTimeout() { return defaultRequestTimeout; }
    public void setDefaultRequestTimeout(Duration defaultRequestTimeout) { this.defaultRequestTimeout = defaultRequestTimeout; }
    public boolean isAutoStartup() { return autoStartup; }
    public void setAutoStartup(boolean autoStartup) { this.autoStartup = autoStartup; }
    public Duration getShutdownTimeout() { return shutdownTimeout; }
    public void setShutdownTimeout(Duration shutdownTimeout) { this.shutdownTimeout = shutdownTimeout; }
    public boolean isConnectionReadiness() { return connectionReadiness; }
    public void setConnectionReadiness(boolean connectionReadiness) { this.connectionReadiness = connectionReadiness; }
    public Duration getMetricsRefresh() { return metricsRefresh; }
    public void setMetricsRefresh(Duration metricsRefresh) { this.metricsRefresh = metricsRefresh; }
    public Tls getTls() { return tls; }
    public Serialization getSerialization() { return serialization; }
    public ConsumerState getConsumerState() { return consumerState; }
    public Tracing getTracing() { return tracing; }
}
