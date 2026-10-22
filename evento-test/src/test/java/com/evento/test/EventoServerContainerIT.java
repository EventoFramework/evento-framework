package com.evento.test;

import com.evento.application.client.BundleClient;
import com.evento.application.client.BundleClientState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A2: the real server image started for a test, on a PostgreSQL of the test, and a bundle
 * connecting to it. EVENTO_TEST_SERVER_IMAGE names another image (a local build) than the
 * published one of this version.
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based IT.")
class EventoServerContainerIT {

    @Test
    void theServerStartsOnTheTestsDatabaseAndABundleConnects() throws Exception {
        String image = System.getenv("EVENTO_TEST_SERVER_IMAGE");
        try (var network = Network.newNetwork();
             var postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                     .withNetwork(network).withNetworkAliases("pg");
             var evento = (image == null || image.isBlank() ? new EventoServerContainer() : new EventoServerContainer(image))
                     .withNetwork(network)
                     .withDatabase("jdbc:postgresql://pg:5432/" + postgres.getDatabaseName(),
                             postgres.getUsername(), postgres.getPassword())
                     .dependsOn(postgres)) {
            evento.start();

            var mode = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(evento.getHttpUrl() + "/api/auth/mode")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(mode.statusCode()).isEqualTo(200);
            assertThat(mode.body()).contains("basic");

            var client = BundleClient.builder("container-it", "container-it-1")
                    .host(evento.getHost())
                    .port(evento.getBusPort())
                    .bundleVersion("1")
                    .handlerPayloadTypes(List.of())
                    .build();
            try {
                client.start().get(30, TimeUnit.SECONDS);
                assertThat(client.state()).isEqualTo(BundleClientState.READY);
            } finally {
                client.stop(Duration.ofSeconds(5));
            }
        }
    }
}
