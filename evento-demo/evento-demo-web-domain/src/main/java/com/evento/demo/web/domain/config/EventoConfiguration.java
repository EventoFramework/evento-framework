package com.evento.demo.web.domain.config;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.common.performance.ThreadCountAutoscalingProtocol;
import com.evento.demo.telemetry.SentryTracingAgent;
import com.evento.demo.web.domain.DemoWebApplication;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

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
