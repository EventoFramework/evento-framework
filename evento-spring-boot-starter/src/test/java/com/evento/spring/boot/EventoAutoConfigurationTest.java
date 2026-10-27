package com.evento.spring.boot;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.consumer.state.store.jdbc.JdbcConsumerStateStore;
import com.evento.opentelemetry.OpenTelemetryTracingAgent;
import com.evento.spring.boot.app.FindPrice;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The auto-configuration without a broker: the bundle is not started ({@code auto-startup=false}). */
class EventoAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventoAutoConfiguration.class))
            .withPropertyValues("evento.bundle.auto-startup=false");

    @Test
    void withoutABundleIdNothingIsConfigured() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(EventoBundleLifecycle.class);
            assertThat(ctx).doesNotHaveBean(CommandGateway.class);
        });
    }

    @Test
    void aBundleIdConfiguresTheLifecycleAndTheGateways() {
        runner.withPropertyValues("evento.bundle.id=orders").run(ctx -> {
            assertThat(ctx).hasSingleBean(EventoBundleLifecycle.class);
            assertThat(ctx).hasSingleBean(CommandGateway.class);
            assertThat(ctx).hasSingleBean(QueryGateway.class);
            assertThat(ctx.getBean(EventoBundleLifecycle.class).isRunning()).isFalse();
        });
    }

    @Test
    void theGatewaysFailClearlyBeforeTheBundleRuns() {
        runner.withPropertyValues("evento.bundle.id=orders").run(ctx -> {
            var queries = ctx.getBean(QueryGateway.class);
            assertThatThrownBy(() -> queries.query(new FindPrice("pen")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("'orders' is not running");
        });
    }

    @Test
    void anApplicationGatewayWins() {
        runner.withPropertyValues("evento.bundle.id=orders")
                .withUserConfiguration(OwnGateway.class)
                .run(ctx -> assertThat(ctx.getBean(CommandGateway.class)).isSameAs(OwnGateway.GATEWAY));
    }

    @Test
    void thePropertiesShapeTheBuilder() {
        runner.withPropertyValues(
                        "evento.bundle.id=orders",
                        "evento.bundle.version=7",
                        "evento.bundle.instance-id=orders-1",
                        "evento.bundle.base-package=com.acme.orders",
                        "evento.bundle.servers=eu-1:3030, eu-2:3031",
                        "evento.bundle.auth-token=s3cret",
                        "evento.bundle.zone=eu",
                        "evento.bundle.serialization.allowed-packages=com.acme.api")
                .run(ctx -> {
                    var builder = ctx.getBean(EventoBundleLifecycle.class).builder();
                    assertThat(builder.getBundleId()).isEqualTo("orders");
                    assertThat(builder.getBundleVersion()).isEqualTo(7);
                    assertThat(builder.getInstanceId()).isEqualTo("orders-1");
                    assertThat(builder.getBasePackageName()).isEqualTo("com.acme.orders");
                    assertThat(builder.getAuthToken()).isEqualTo("s3cret");
                    assertThat(builder.getZone()).isEqualTo("eu");
                    assertThat(builder.getAllowedPayloadPackages()).containsExactly("com.acme.api");
                    assertThat(builder.getEventoServerMessageBusConfiguration().getAddresses())
                            .extracting(a -> a.serverAddress() + ":" + a.serverPort())
                            .containsExactly("eu-1:3030", "eu-2:3031");
                });
    }

    @Test
    void withoutABasePackageOrAnApplicationPackageTheBuilderSaysWhatIsMissing() {
        runner.withPropertyValues("evento.bundle.id=orders").run(ctx ->
                assertThatThrownBy(() -> ctx.getBean(EventoBundleLifecycle.class).builder())
                        .hasMessageContaining("evento.bundle.base-package"));
    }

    @Test
    void halfAMutualTlsConfigurationIsRejected() {
        var tls = new EventoProperties.Tls();
        tls.setEnabled(true);
        tls.setCertChain("bundle.pem");
        assertThatThrownBy(() -> EventoBundleLifecycle.clientContext(tls))
                .hasMessageContaining("cert-chain and private-key go together");
    }

    @Test
    void aServerAddressNeedsHostAndPort() {
        assertThat(EventoBundleLifecycle.address(" evento:3030 ").serverPort()).isEqualTo(3030);
        assertThatThrownBy(() -> EventoBundleLifecycle.address("evento"))
                .hasMessageContaining("expected host:port");
    }

    @Test
    void theOptionalIntegrationsStayAwayWhenTheirLibrariesAreMissing() {
        runner.withPropertyValues("evento.bundle.id=orders")
                .withClassLoader(new FilteredClassLoader(OpenTelemetryTracingAgent.class, OpenTelemetry.class,
                        JdbcConsumerStateStore.class, io.micrometer.core.instrument.MeterRegistry.class))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(EventoBundleCustomizer.class);
                    assertThat(ctx).doesNotHaveBean(EventoBundleMetrics.class);
                });
    }

    @Test
    void theOptionalIntegrationsRegisterWhenTheirLibrariesArePresent() {
        runner.withPropertyValues("evento.bundle.id=orders").run(ctx ->
                assertThat(ctx.getBeansOfType(EventoBundleCustomizer.class)).hasSize(2));
        runner.withPropertyValues("evento.bundle.id=orders", "evento.bundle.tracing.enabled=false").run(ctx ->
                assertThat(ctx.getBeansOfType(EventoBundleCustomizer.class)).containsOnlyKeys("eventoJdbcConsumerState"));
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnGateway {
        static final CommandGateway GATEWAY = new CommandGateway() {
            @Override
            public <R> CompletableFuture<R> send(com.evento.common.modeling.messaging.payload.Command command,
                                                 com.evento.common.modeling.messaging.message.application.Metadata metadata,
                                                 com.evento.common.modeling.messaging.message.application.Message<?> handledMessage,
                                                 long timeout, TimeUnit unit) {
                return CompletableFuture.completedFuture(null);
            }
        };

        @Bean
        CommandGateway ownGateway() {
            return GATEWAY;
        }
    }
}
