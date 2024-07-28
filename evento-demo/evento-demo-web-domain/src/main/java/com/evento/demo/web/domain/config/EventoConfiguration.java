package com.evento.demo.web.domain.config;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.common.messaging.gateway.CommandGatewayImpl;
import com.evento.common.messaging.gateway.QueryGatewayImpl;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.payload.Command;
import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.QueryResponse;
import com.evento.common.performance.ThreadCountAutoscalingProtocol;
import com.evento.demo.telemetry.SentryTracingAgent;
import com.evento.demo.web.domain.DemoWebApplication;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Configuration
public class EventoConfiguration {

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
    public EventoBundle eventoApplication(
            @Value("${evento.server.host}") String eventoServerHost,
            @Value("${evento.server.port}") Integer eventoServerPort,
            @Value("${evento.bundle.id}") String bundleId,
            @Value("${evento.bundle.version}") long bundleVersion,
            @Value("${evento.cluster.autoscaling.max.threads}") int maxThreads,
            @Value("${evento.cluster.autoscaling.max.overflow}") int maxOverflow,
            @Value("${evento.cluster.autoscaling.min.threads}") int minThreads,
            @Value("${evento.cluster.autoscaling.max.underflow}") int maxUnderflow,
            BeanFactory factory,
            @Value("${sentry.dns}") String sentryDns
    ) throws Exception {
        return EventoBundle.Builder.builder()
                .setBasePackage(DemoWebApplication.class.getPackage())
                .setBundleId(bundleId)
                .setBundleVersion(bundleVersion)
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                                new ClusterNodeAddress(eventoServerHost, eventoServerPort)
                        )
                                .setDisableDelayMillis(1000)
                                .setMaxDisableAttempts(3)
                                .setMaxReconnectAttempts(30)
                                .setReconnectDelayMillis(5000)
                )
                .setCommandGatewayBuilder(es -> new CommandGatewayImpl(es){

                    @Override
                    public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage) {
                        if(metadata == null){
                            metadata = new Metadata();
                        }
                        Metadata finalMetadata = metadata;
                        Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                                .filter(ServletRequestAttributes.class::isInstance)
                                .map(ServletRequestAttributes.class::cast)
                                .map(ServletRequestAttributes::getRequest)
                                .ifPresent(r -> {
                                    finalMetadata.invalidateCache(r.getHeader("Invalidate-Evento-Cache").equals("true"));
                                    finalMetadata.forceTelemetry(r.getHeader("Force-Evento-Telemetry").equals("true"));
                                });
                        return super.send(command, metadata, handledMessage);
                    }
                })
                .setQueryGatewayBuilder(es -> new QueryGatewayImpl(es){
                    @Override
                    public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, Metadata metadata, Message<?> handledMessage) {
                        if(metadata == null){
                            metadata = new Metadata();
                        }
                        Metadata finalMetadata = metadata;
                        Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                                .filter(ServletRequestAttributes.class::isInstance)
                                .map(ServletRequestAttributes.class::cast)
                                .map(ServletRequestAttributes::getRequest)
                                .ifPresent(r -> {
                                    finalMetadata.invalidateCache(r.getHeader("Invalidate-Evento-Cache").equals("true"));
                                    finalMetadata.forceTelemetry(r.getHeader("Force-Evento-Telemetry").equals("true"));
                                });
                        return super.query(query, metadata, handledMessage);
                    }
                })
                .setTracingAgent(new SentryTracingAgent(bundleId, bundleVersion, sentryDns))
                .setAutoscalingProtocolBuilder((es) -> new ThreadCountAutoscalingProtocol(
                        es,
                        maxThreads,
                        minThreads,
                        maxOverflow,
                        maxUnderflow, 60 * 1000))
                .setInjector(factory::getBean)
                .start();

    }
}
