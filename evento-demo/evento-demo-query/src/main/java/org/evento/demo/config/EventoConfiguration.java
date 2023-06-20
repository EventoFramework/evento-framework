package org.evento.demo.config;

import org.evento.application.EventoBundle;
import org.evento.bus.rabbitmq.RabbitMqMessageBus;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.performance.ThreadCountAutoscalingProtocol;
import org.evento.common.serialization.ObjectMapperUtils;
import org.evento.consumer.state.store.mysql.MysqlConsumerStateStore;
import org.evento.demo.DemoQueryApplication;
import org.evento.demo.telemetry.SentryTracingAgent;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.sql.DriverManager;

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
			@Value("${spring.datasource.url}") String connectionUrl,
			@Value("${spring.datasource.username}") String username,
			@Value("${spring.datasource.password}") String password,
			@Value("${sentry.dns}") String sentryDns
	) throws Exception {
		MessageBus messageBus = RabbitMqMessageBus.create(bundleId,
				bundleVersion,
				channelName,
				rabbitHost,
				10);
		return EventoBundle.Builder.builder()
				.setBasePackage(DemoQueryApplication.class.getPackage())
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
				.setConsumerStateStore(new MysqlConsumerStateStore(messageBus, bundleId, serverName, DriverManager.getConnection(
						connectionUrl, username, password)))
				.setInjector(factory::getBean)
				.start();

	}
}
