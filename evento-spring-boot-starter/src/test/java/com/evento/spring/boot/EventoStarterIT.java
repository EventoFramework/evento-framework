package com.evento.spring.boot;

import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.correlation.ForwardingDedupCache;
import com.evento.server.bus.event.BusEventBus;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
import com.evento.server.bus.security.TokenValidator;
import com.evento.spring.boot.app.FindPrice;
import com.evento.spring.boot.app.PriceBook;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationListener;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The starter end to end over real TCP: a broker requiring a token, a Spring context whose only
 * Evento configuration is properties, a query through the injected {@link QueryGateway}, answered
 * by a projection built with a Spring bean, traced with the application's OpenTelemetry.
 */
@Timeout(60)
class EventoStarterIT {

    private static final String TOKEN = "starter-token";
    /** New per test: closing a context closes its SDK, and the SDK shuts its exporter down. */
    private static volatile InMemorySpanExporter SPANS;

    private BusLifecycle broker;
    private int port;

    @BeforeEach
    void startBroker() {
        port = startBrokerOn(0);
        SPANS = InMemorySpanExporter.create();
    }

    private int startBrokerOn(int requestedPort) {
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        broker = new BusLifecycle(new NettyServerTransport(NettyTransportConfig.defaults()), connections,
                new ClusterRegistry(connections), new CorrelationStore(Duration.ofMillis(100)), new ForwardingTable(),
                eventBus, "starter-broker", Set.of(HandshakeProtocol.CAPABILITY_PING_PONG),
                new JacksonCborPayloadCodec(), TokenValidator.sharedSecret(TOKEN),
                new ForwardingDedupCache(1000, Duration.ofMinutes(1)));
        return broker.start(requestedPort);
    }

    @AfterEach
    void stopBroker() {
        broker.stop(Duration.ofMillis(200));
    }

    private ApplicationContextRunner app() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(EventoAutoConfiguration.class))
                .withUserConfiguration(Beans.class)
                .withPropertyValues(
                        "evento.bundle.id=prices",
                        "evento.bundle.servers=127.0.0.1:" + port,
                        "evento.bundle.auth-token=" + TOKEN,
                        "evento.bundle.base-package=com.evento.spring.boot.app",
                        "evento.bundle.shutdown-timeout=2s");
    }

    @Test
    void aQueryThroughTheInjectedGatewayIsAnsweredByTheProjection() {
        app().run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(EventoBundleLifecycle.class).isRunning()).isTrue();

            awaitEnabled();
            var answer = ctx.getBean(QueryGateway.class).query(new FindPrice("ink")).get(10, TimeUnit.SECONDS);

            assertThat(answer.getData().getItem()).isEqualTo("ink");
            assertThat(answer.getData().getPrice()).isEqualTo(7);
        });
    }

    @Test
    void theTraceOfTheCallerReachesTheHandlerThroughTheBroker() {
        app().run(ctx -> {
            var tracer = ctx.getBean(OpenTelemetry.class).getTracer("test");
            var request = tracer.spanBuilder("GET /prices/pen").setSpanKind(SpanKind.SERVER).startSpan();
            awaitEnabled();
            try (var ignored = request.makeCurrent()) {
                ctx.getBean(QueryGateway.class).query(new FindPrice("pen")).get(10, TimeUnit.SECONDS);
            } finally {
                request.end();
            }

            await().atMost(5, TimeUnit.SECONDS).until(() -> SPANS.getFinishedSpanItems().size() >= 3);
            var send = SPANS.getFinishedSpanItems().stream()
                    .filter(s -> s.getName().equals("query FindPrice")).findFirst().orElseThrow();
            var handler = SPANS.getFinishedSpanItems().stream()
                    .filter(s -> s.getName().startsWith("PriceProjection")).findFirst().orElseThrow();
            assertThat(send.getParentSpanId()).isEqualTo(request.getSpanContext().getSpanId());
            assertThat(handler.getTraceId()).isEqualTo(request.getSpanContext().getTraceId());
            assertThat(handler.getParentSpanId()).isEqualTo(send.getSpanId());
        });
    }

    @Test
    void closingTheContextTakesTheBundleOffTheBroker() {
        app().run(ctx -> {
            await().atMost(5, TimeUnit.SECONDS).until(() -> !broker.availableView().isEmpty());
            ctx.close();
            await().atMost(5, TimeUnit.SECONDS).until(() -> broker.availableView().isEmpty());
        });
    }

    @Test
    void theBundlesMeasurementsReachTheMeterRegistry() {
        app().withPropertyValues("evento.bundle.metrics-refresh=100ms").run(ctx -> {
            awaitEnabled();
            ctx.getBean(QueryGateway.class).query(new FindPrice("pen")).get(10, TimeUnit.SECONDS);
            var registry = ctx.getBean(MeterRegistry.class);

            assertThat(registry.get("evento.bundle.connected").tag("bundle", "prices").gauge().value()).isEqualTo(1);
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                var handler = registry.get("evento.bundle.handler").tag("type", "query").functionTimer();
                assertThat(handler.count()).isGreaterThanOrEqualTo(1);
                assertThat(registry.get("evento.bundle.handler.p99").tag("type", "query").timeGauge()).isNotNull();
            });
        });
    }

    @Test
    void withConnectionReadinessTheApplicationRefusesTrafficWhileTheBundleReconnects() {
        app().withPropertyValues("evento.bundle.connection-readiness=true")
                .withUserConfiguration(ReadinessLog.class)
                .run(ctx -> {
                    var log = ctx.getBean(ReadinessLog.class);
                    awaitEnabled();
                    broker.stop(Duration.ofMillis(200));
                    await().atMost(10, TimeUnit.SECONDS).until(() -> log.last() == ReadinessState.REFUSING_TRAFFIC);
                    startBrokerOn(port);
                    await().atMost(30, TimeUnit.SECONDS).until(() -> log.last() == ReadinessState.ACCEPTING_TRAFFIC);
                });
    }

    @Test
    void withoutConnectionReadinessTheBundleLeavesReadinessAlone() {
        app().withUserConfiguration(ReadinessLog.class).run(ctx -> {
            assertThat(ctx).doesNotHaveBean(EventoBundleReadiness.class);
            broker.stop(Duration.ofMillis(200));
            Thread.sleep(1000);
            assertThat(ctx.getBean(ReadinessLog.class).last()).isNull();
        });
    }

    /** The bundle answers once the broker routes to it, which trails the context's start. */
    private void awaitEnabled() {
        await().atMost(10, TimeUnit.SECONDS).until(() -> !broker.availableView().isEmpty());
    }

    /** Records the readiness changes published in the context. */
    static class ReadinessLog implements ApplicationListener<AvailabilityChangeEvent<ReadinessState>> {
        private volatile ReadinessState last;

        @Override
        public void onApplicationEvent(AvailabilityChangeEvent<ReadinessState> event) {
            last = event.getState();
        }

        ReadinessState last() {
            return last;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Beans {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        PriceBook priceBook() {
            return new PriceBook();
        }

        @Bean(destroyMethod = "close")
        OpenTelemetrySdk openTelemetry() {
            return OpenTelemetrySdk.builder()
                    .setTracerProvider(SdkTracerProvider.builder()
                            .addSpanProcessor(SimpleSpanProcessor.create(SPANS)).build())
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .build();
        }
    }
}
