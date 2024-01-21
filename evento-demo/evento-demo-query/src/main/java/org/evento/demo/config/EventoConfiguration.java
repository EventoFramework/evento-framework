package org.evento.demo.config;

import org.evento.application.EventoBundle;
import org.evento.application.bus.ClusterNodeAddress;
import org.evento.application.bus.EventoServerMessageBusConfiguration;
import org.evento.common.messaging.consumer.impl.InMemoryConsumerStateStore;
import org.evento.common.performance.ThreadCountAutoscalingProtocol;
import org.evento.demo.DemoQueryApplication;
import org.evento.demo.telemetry.SentryTracingAgent;
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
			@Value("${spring.datasource.url}") String connectionUrl,
			@Value("${spring.datasource.username}") String username,
			@Value("${spring.datasource.password}") String password,
			@Value("${sentry.dns}") String sentryDns
	) throws Exception {
		return EventoBundle.Builder.builder()
				.setBasePackage(DemoQueryApplication.class.getPackage())
				.setConsumerStateStoreBuilder(InMemoryConsumerStateStore::new)
				.setInjector(factory::getBean)
				.setBundleId(bundleId)
				.setBundleVersion(bundleVersion)
				.setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
						new ClusterNodeAddress(eventoServerHost, eventoServerPort)
				).setDisableDelayMillis(1000).setMaxDisableAttempts(3)
						.setMaxReconnectAttempts(30)
						.setReconnectDelayMillis(5000))
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
