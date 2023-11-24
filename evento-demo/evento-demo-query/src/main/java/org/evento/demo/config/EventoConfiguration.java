package org.evento.demo.config;

import org.evento.application.EventoBundle;
import org.evento.application.bus.ClusterNodeAddress;
import org.evento.application.bus.MessageBusConfiguration;
import org.evento.common.performance.ThreadCountAutoscalingProtocol;
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
import java.sql.SQLException;

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
		return EventoBundle.Builder.builder()
				.setBasePackage(DemoQueryApplication.class.getPackage())
				.setConsumerStateStoreBuilder((es, ps) -> {
					try {
						return new MysqlConsumerStateStore(es, ps, DriverManager.getConnection(connectionUrl, username, password));
					} catch (SQLException e) {
						throw new RuntimeException(e);
					}
				})
				.setInjector(factory::getBean)
				.setBundleId(bundleId)
				.setBundleVersion(bundleVersion)
				.setMessageBusConfiguration(new MessageBusConfiguration(
						new ClusterNodeAddress("localhost",3030)
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
