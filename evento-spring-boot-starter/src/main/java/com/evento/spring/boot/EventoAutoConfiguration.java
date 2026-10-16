package com.evento.spring.boot;

import com.evento.application.EventoBundle;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.common.messaging.consumer.ConsumerExecutors;
import com.evento.common.messaging.consumer.ConsumerProcessor;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.serialization.ObjectMapperUtils;
import com.evento.consumer.state.store.jdbc.JdbcConsumerLock;
import com.evento.consumer.state.store.jdbc.JdbcConsumerStateStore;
import com.evento.consumer.state.store.jdbc.JdbcDeadEventQueue;
import com.evento.consumer.state.store.jdbc.JdbcDedupeStore;
import com.evento.consumer.state.store.jdbc.JdbcSagaStateStore;
import com.evento.consumer.state.store.jdbc.SqlDialect;
import com.evento.opentelemetry.OpenTelemetryTracingAgent;
import io.opentelemetry.api.OpenTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;

/**
 * Runs an Evento bundle in a Spring Boot application when {@code evento.bundle.id} is set.
 *
 * <ul>
 *   <li>{@link EventoBundleLifecycle}: builds the bundle from {@link EventoProperties} and the
 *       {@link EventoBundleCustomizer} beans, starts it after the context is ready, stops it
 *       gracefully on shutdown;</li>
 *   <li>{@link CommandGateway} and {@link QueryGateway} beans for application code;</li>
 *   <li>with {@code evento-consumer-state-store-jdbc} and a {@link DataSource}: consumer checkpoints,
 *       saga state, dead events and dedupe in the database ({@code evento.bundle.consumer-state.*});</li>
 *   <li>with {@code evento-opentelemetry} and an {@link OpenTelemetry} bean: a span per handler and
 *       trace context across bundles ({@code evento.bundle.tracing.*}).</li>
 * </ul>
 *
 * <p>Nothing here is required: an application can keep building {@link EventoBundle} by hand, and
 * Evento itself does not depend on Spring.
 */
@AutoConfiguration
@ConditionalOnClass(EventoBundle.class)
@ConditionalOnProperty(prefix = "evento.bundle", name = "id")
@EnableConfigurationProperties(EventoProperties.class)
public class EventoAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EventoAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public EventoBundleLifecycle eventoBundleLifecycle(EventoProperties properties, ListableBeanFactory beanFactory,
                                                       ObjectProvider<EventoBundleCustomizer> customizers) {
        return new EventoBundleLifecycle(properties, beanFactory, customizers);
    }

    @Bean
    @ConditionalOnMissingBean
    public CommandGateway eventoCommandGateway(EventoBundleLifecycle lifecycle) {
        return new BundleGateways.Commands(lifecycle);
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryGateway eventoQueryGateway(EventoBundleLifecycle lifecycle) {
        return new BundleGateways.Queries(lifecycle);
    }

    /** Consumer state in the application's database. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.evento.consumer.state.store.jdbc.JdbcConsumerStateStore")
    static class JdbcConsumerStateConfiguration {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        EventoBundleCustomizer eventoJdbcConsumerState(EventoProperties properties,
                                                       ObjectProvider<DataSource> dataSource) {
            return builder -> {
                var state = properties.getConsumerState();
                if (state.getStore() == EventoProperties.ConsumerState.Store.MEMORY) return;
                var ds = dataSource.getIfUnique();
                if (ds == null) {
                    if (state.getStore() == EventoProperties.ConsumerState.Store.JDBC) {
                        throw new IllegalStateException("evento.bundle.consumer-state.store=jdbc needs one DataSource bean");
                    }
                    return;
                }
                var dialect = SqlDialect.valueOf(state.getDialect().name());
                log.info("event=evento_consumer_state store=jdbc dialect={}", dialect);
                builder.setConsumerEngineConfigBuilder((server, performance) -> {
                    var mapper = ObjectMapperUtils.getPayloadObjectMapper();
                    var stateStore = new JdbcConsumerStateStore(ds, dialect);
                    var deadEvents = new JdbcDeadEventQueue(ds, dialect, mapper);
                    var processor = ConsumerProcessor.builder()
                            .eventoServer(server)
                            .lock(new JdbcConsumerLock(ds, dialect))
                            .stateStore(stateStore)
                            .sagaStateStore(new JdbcSagaStateStore(ds, dialect, mapper))
                            .deadEventQueue(deadEvents)
                            .dedupeStore(new JdbcDedupeStore(ds, dialect))
                            .performanceService(performance)
                            .observerExecutor(ConsumerExecutors.virtual("observer",
                                    ConsumerEngineConfig.DEFAULT_OBSERVER_CONCURRENCY))
                            .build();
                    return new ConsumerEngineConfig(processor, stateStore, deadEvents);
                });
            };
        }
    }

    /** Tracing with the application's OpenTelemetry. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"com.evento.opentelemetry.OpenTelemetryTracingAgent",
            "io.opentelemetry.api.OpenTelemetry"})
    @ConditionalOnProperty(prefix = "evento.bundle.tracing", name = "enabled", matchIfMissing = true)
    static class TracingConfiguration {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        EventoBundleCustomizer eventoOpenTelemetryTracing(EventoProperties properties,
                                                          ObjectProvider<OpenTelemetry> openTelemetry) {
            return builder -> {
                var otel = openTelemetry.getIfUnique();
                if (otel == null) return;
                var agent = new OpenTelemetryTracingAgent(properties.getId(), properties.getVersion(), otel);
                builder.setTracingAgent(properties.getTracing().isLinkConsumers() ? agent.linkingConsumers() : agent);
                log.info("event=evento_tracing provider=opentelemetry link_consumers={}",
                        properties.getTracing().isLinkConsumers());
            };
        }
    }
}
