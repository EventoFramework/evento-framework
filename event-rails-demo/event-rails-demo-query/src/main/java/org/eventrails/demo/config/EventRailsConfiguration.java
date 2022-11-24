package org.eventrails.demo.config;

import org.eventrails.application.EventRailsApplication;
import org.eventrails.bus.rabbitmq.RabbitMqMessageBus;
import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.performance.ThreadCountAutoscalingProtocol;
import org.eventrails.consumer.state.store.mysql.MysqlConsumerStateStore;
import org.eventrails.demo.DemoQueryApplication;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;

@Configuration
public class EventRailsConfiguration {

	@Bean
	public EventRailsApplication eventRailsApplication(
			@Value("${eventrails.cluster.message.channel.name}") String channelName,
			@Value("${eventrails.cluster.node.server.id}") String serverName,
			@Value("${eventrails.bundle.id}") String bundleId,
			@Value("${eventrails.bundle.version}") long bundleVersion,
			@Value("${eventrails.cluster.rabbitmq.host}") String rabbitHost,
			@Value("${eventrails.cluster.autoscaling.max.threads}") int maxThreads,
			@Value("${eventrails.cluster.autoscaling.max.overflow}") int maxOverflow,
			@Value("${eventrails.cluster.autoscaling.min.threads}") int minThreads,
			@Value("${eventrails.cluster.autoscaling.max.underflow}") int maxUnderflow,
			BeanFactory factory,
			@Value("${spring.datasource.url}") String connectionUrl,
			@Value("${spring.datasource.username}") String username,
			@Value("${spring.datasource.password}") String password
	) throws Exception {

		MessageBus messageBus = RabbitMqMessageBus.create(bundleId, bundleVersion, channelName, rabbitHost);
		return EventRailsApplication.start(DemoQueryApplication.class.getPackage().getName(),
				bundleId,
				bundleVersion,
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
