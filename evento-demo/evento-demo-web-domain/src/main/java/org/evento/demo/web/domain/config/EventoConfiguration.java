package org.evento.demo.web.domain.config;

import org.evento.application.EventoBundle;
import org.evento.bus.rabbitmq.RabbitMqMessageBus;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.performance.ThreadCountAutoscalingProtocol;
import org.evento.demo.telemetry.SentryTracingAgent;
import org.evento.demo.web.domain.DemoWebApplication;
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
			@Value("${evento.cluster.message.channel.name}") String channelName,
			@Value("${evento.cluster.node.server.id}") String serverName,
			@Value("${evento.bundle.id}") String bundleId,
			@Value("${evento.bundle.version}") long bundleVersion,
			@Value("${evento.cluster.rabbitmq.host}") String rabbitHost,
			@Value("${evento.cluster.autoscaling.max.threads}") int maxThreads,
			@Value("${evento.cluster.autoscaling.max.overflow}") int maxOverflow,
			@Value("${evento.cluster.autoscaling.min.threads}") int minThreads,
			@Value("${evento.cluster.autoscaling.max.underflow}") int maxUnderflow,
			BeanFactory factory,
			@Value("${sentry.dns}") String sentryDns
	) throws Exception {

		MessageBus messageBus = RabbitMqMessageBus.create(bundleId,
				bundleVersion,
				channelName,
				rabbitHost,
				10);
		return EventoBundle.Builder.builder()
				.setBasePackage(DemoWebApplication.class.getPackage())
				.setBundleId(bundleId)
				.setBundleVersion(bundleVersion)
				.setServerName(serverName)
				.setMessageBus(messageBus)
				.setTracingAgent(new SentryTracingAgent(sentryDns))
				.setAutoscalingProtocol(new ThreadCountAutoscalingProtocol(
						bundleId,
						serverName,
						messageBus,
						maxThreads,
						minThreads,
						maxOverflow,
						maxUnderflow))
				.setInjector(factory::getBean)
				.start();

	}
}
