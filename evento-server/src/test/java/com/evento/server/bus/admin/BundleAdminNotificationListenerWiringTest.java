package com.evento.server.bus.admin;

import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.event.BusEventBus;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
import com.evento.server.service.discovery.ConsumerService;
import com.evento.server.service.performance.PerformanceStoreService;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.codec.JacksonCborCodec;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for the two DI traps that silently killed
 * {@link BundleAdminNotificationListener} in production — consumer
 * registrations and performance telemetry were dropped for months — while
 * every wire-level IT stayed green (they subscribe to the event bus directly
 * and never boot Spring):
 *
 * <ul>
 *   <li>the {@code @ConditionalOnProperty} prefix referenced the pre-rename
 *       {@code evento.server.bus.v2} flag that no configuration ever set;</li>
 *   <li>{@code @ConditionalOnBean(BusLifecycle.class)} on a component-scanned
 *       class is evaluated during the scan, before any {@code @Configuration}
 *       {@code @Bean} definitions are registered, and therefore never matched
 *       (Spring restricts {@code @ConditionalOnBean} to auto-configurations
 *       for exactly this reason).</li>
 * </ul>
 *
 * <p>The listener is discovered here exactly as in production (component
 * scan) with {@code BusLifecycle} provided by a {@code @Configuration} class,
 * so either regression trips this test.
 */
class BundleAdminNotificationListenerWiringTest {

    @Configuration
    @ComponentScan(basePackageClasses = BundleAdminNotificationListener.class)
    static class AdminPackageScan {
    }

    @Configuration
    static class Collaborators {

        @Bean
        BusLifecycle busLifecycle() {
            var eventBus = new BusEventBus();
            var connections = new ConnectionRegistry(eventBus);
            var config = new NettyTransportConfig(
                    Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(5),
                    1024 * 1024, 64 * 1024, 32 * 1024,
                    new ExponentialBackoffWithJitter(), new JacksonCborCodec(),
                    Executors.newVirtualThreadPerTaskExecutor());
            // Never started — the wiring test only needs the bean definition.
            return new BusLifecycle(new NettyServerTransport(config), connections,
                    new ClusterRegistry(connections), new CorrelationStore(Duration.ofSeconds(1)),
                    new ForwardingTable(), eventBus, "wiring-test-server",
                    Set.of(HandshakeProtocol.CAPABILITY_PING_PONG), new JacksonCborPayloadCodec());
        }

        // Constructors are assignment-only, so null collaborators are safe for
        // a context-wiring assertion (nothing is invoked on them).
        @Bean
        PerformanceStoreService performanceStoreService() {
            return new PerformanceStoreService(null, null, null, 1.0, null, null, null);
        }

        @Bean
        ConsumerService consumerService() {
            return new ConsumerService(null, null, null, "wiring-test-server", null);
        }
    }

    @Test
    void listenerIsWiredWhenBusEnabled() {
        new ApplicationContextRunner()
                .withPropertyValues("evento.server.bus.enabled=true", "evento.telemetry.ttl=365")
                .withUserConfiguration(Collaborators.class, AdminPackageScan.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(BundleAdminNotificationListener.class));
    }

    @Test
    void listenerIsAbsentWhenBusDisabled() {
        new ApplicationContextRunner()
                .withPropertyValues("evento.server.bus.enabled=false", "evento.telemetry.ttl=365")
                .withUserConfiguration(Collaborators.class, AdminPackageScan.class)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(BundleAdminNotificationListener.class));
    }
}
