package com.evento.lab.gate;

import com.evento.application.client.BundleClient;
import com.evento.lab.support.EmbeddedBroker;
import com.evento.transport.protocol.ProtocolPayloadTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A bundle's start() returns once the broker has handled its registration, however long that
 * takes. On a fresh catalog the broker writes a large bundle's discovery one handler at a time
 * (670 handlers: 8.5 s), beyond the registration timeout; the barrier that ends the registration
 * waited only that long, so start() returned early and the commands an application sends at
 * startup (seeders) found no handler.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class SlowDiscoveryIT {

    private EmbeddedBroker broker;
    private BundleClient client;

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (broker != null) broker.close();
    }

    @Test
    void startWaitsForARegistrationSlowerThanTheRegistrationTimeout() throws Exception {
        broker = new EmbeddedBroker();
        // Stands for a slow discovery: the barrier is answered after what came before it.
        broker.lifecycle().registerLocalHandler(ProtocolPayloadTypes.BARRIER, payload -> {
            Thread.sleep(1_500);
            return new byte[0];
        });
        client = BundleClient.builder("lab-slow-discovery", "slow-1")
                .host("127.0.0.1")
                .port(broker.port())
                .handlerPayloadTypes(List.of("SlowPing"))
                .registrationTimeout(Duration.ofMillis(300))
                .build();
        client.registerRequestHandler("SlowPing", (payload, ctx) -> payload);

        long start = System.nanoTime();
        client.start().get(30, TimeUnit.SECONDS);

        assertThat(Duration.ofNanos(System.nanoTime() - start))
                .as("start() waited for the barrier, not only the registration timeout")
                .isGreaterThanOrEqualTo(Duration.ofMillis(1_400));
        assertThat(broker.lifecycle().isBundleAvailable("lab-slow-discovery")).isTrue();
    }
}
