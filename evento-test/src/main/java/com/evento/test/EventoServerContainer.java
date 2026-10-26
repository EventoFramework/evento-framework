package com.evento.test;

import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

/**
 * The real Evento server in a container, for integration tests of an application against it
 * (Testcontainers): the same image as in production, with its event store on a PostgreSQL the
 * test provides. Unlike {@code EventoTestBroker} ({@code evento-test-broker}) it runs out of
 * process, so it brings neither the server's dependencies nor its Java 25 into the application's
 * tests, and works whatever Spring Boot version the application uses.
 *
 * <pre>{@code
 * Network network = Network.newNetwork();
 * PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
 *         .withNetwork(network).withNetworkAliases("pg");
 * EventoServerContainer evento = new EventoServerContainer()
 *         .withNetwork(network)
 *         .withDatabase("jdbc:postgresql://pg:5432/" + postgres.getDatabaseName(),
 *                 postgres.getUsername(), postgres.getPassword())
 *         .dependsOn(postgres);
 * evento.start();
 * EventoBundle.Builder.builder()...setEventoServerMessageBusConfiguration(evento.busConfiguration())...
 * }</pre>
 *
 * <p>Testcontainers is not a dependency of {@code evento-test}: add
 * {@code org.testcontainers:testcontainers} (and the PostgreSQL module) to the tests that use this.
 */
public class EventoServerContainer extends GenericContainer<EventoServerContainer> {

    /** The message bus port bundles connect to. */
    public static final int BUS_PORT = 3030;
    /** The web console, REST API and actuator port. */
    public static final int HTTP_PORT = 3000;
    /** The published image, without its tag: Docker Hub, which anyone can pull without logging in. */
    public static final String IMAGE = "eventoframework/evento-server";

    /** The server image of this version of {@code evento-test}. */
    public EventoServerContainer() {
        this(IMAGE + ":" + version());
    }

    /** Another image or tag, e.g. a locally built one. */
    public EventoServerContainer(String image) {
        super(DockerImageName.parse(image));
        withExposedPorts(BUS_PORT, HTTP_PORT);
        // A test server: 3.0 defaults to prod, which refuses to start without bus TLS, a token and
        // the rest. withSecurityProfile("prod") tests the hardened configuration.
        withEnv("EVENTO_SERVER_SECURITY_PROFILE", "dev");
        waitingFor(Wait.forHttp("/actuator/health").forPort(HTTP_PORT).forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(3)));
    }

    /** The event store database, as the server's container reaches it (a network alias, not localhost). */
    public EventoServerContainer withDatabase(String jdbcUrl, String username, String password) {
        withEnv("SPRING_DATASOURCE_URL", jdbcUrl);
        withEnv("SPRING_DATASOURCE_USERNAME", username);
        withEnv("SPRING_DATASOURCE_PASSWORD", password);
        return self();
    }

    /** {@code dev} (this container's default) or {@code prod} ({@code evento.server.security.profile}). */
    public EventoServerContainer withSecurityProfile(String profile) {
        return withEnv("EVENTO_SERVER_SECURITY_PROFILE", profile);
    }

    /** The shared secret bundles must present ({@code evento.server.bus.auth-token}). */
    public EventoServerContainer withAuthToken(String token) {
        return withEnv("EVENTO_SERVER_BUS_AUTH_TOKEN", token);
    }

    /** The bus port on the test's host. */
    public int getBusPort() {
        return getMappedPort(BUS_PORT);
    }

    /** The base URL of the web console and REST API on the test's host. */
    public String getHttpUrl() {
        return "http://" + getHost() + ":" + getMappedPort(HTTP_PORT);
    }

    /** The connection settings of a bundle that should use this server. */
    public EventoServerMessageBusConfiguration busConfiguration() {
        return new EventoServerMessageBusConfiguration(new ClusterNodeAddress(getHost(), getBusPort()));
    }

    static String version() {
        var properties = new Properties();
        try (InputStream in = EventoServerContainer.class.getResourceAsStream("/evento-test.properties")) {
            if (in != null) properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read evento-test.properties", e);
        }
        String version = properties.getProperty("version");
        if (version == null || version.isBlank() || version.startsWith("@")) {
            throw new IllegalStateException("evento-test does not know its version: name the image explicitly");
        }
        return version;
    }
}
