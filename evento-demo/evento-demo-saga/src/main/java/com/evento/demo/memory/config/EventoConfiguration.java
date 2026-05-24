package com.evento.demo.memory.config;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.demo.DemoSagaApplication;
import com.evento.demo.telemetry.SentryTracingAgent;
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
			BeanFactory factory,
			@Value("${sentry.dns}") String sentryDns
	) throws Exception {
		return EventoBundle.Builder.builder()
				.setBasePackage(DemoSagaApplication.class.getPackage())
				.setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
				.setInjector(factory::getBean)
				.setBundleId(bundleId)
				.setBundleVersion(bundleVersion)
				.setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
						new ClusterNodeAddress(eventoServerHost, eventoServerPort)
				))
				.setTracingAgent(new SentryTracingAgent(bundleId, bundleVersion, sentryDns))
				.setInjector(factory::getBean)
				.start();
	}
}
