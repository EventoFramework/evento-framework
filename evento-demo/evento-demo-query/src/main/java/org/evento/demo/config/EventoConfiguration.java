package org.evento.demo.config;

import org.evento.application.EventoBundle;
import org.evento.bus.rabbitmq.RabbitMqMessageBus;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.performance.ThreadCountAutoscalingProtocol;
import org.evento.consumer.state.store.mysql.MysqlConsumerStateStore;
import org.evento.demo.DemoQueryApplication;
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
			@Value("${evento.bundle.autorun:false}") boolean autorun,
			@Value("${evento.bundle.instances.min:0}") int minInstances,
			@Value("${evento.bundle.instances.max:64}") int maxInstances,
			BeanFactory factory,
			@Value("${spring.datasource.url}") String connectionUrl,
			@Value("${spring.datasource.username}") String username,
			@Value("${spring.datasource.password}") String password
	) throws Exception {

		MessageBus messageBus = RabbitMqMessageBus.create(bundleId, bundleVersion, channelName, rabbitHost);

		return EventoBundle.start(DemoQueryApplication.class.getPackage().getName(),
				bundleId,
				bundleVersion,
				autorun,
				minInstances,
				maxInstances,
				serverName,
				messageBus,
				new ThreadCountAutoscalingProtocol(
						bundleId,
						serverName,
						messageBus,
						maxThreads,
						minThreads,
						maxOverflow,
						maxUnderflow),
				new MysqlConsumerStateStore(messageBus, bundleId, serverName, DriverManager.getConnection(
						connectionUrl, username, password)),
				factory::getBean
		);
	}
}
