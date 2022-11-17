package org.eventrails.demo.config;

import org.eventrails.application.EventRailsApplication;
import org.eventrails.bus.rabbitmq.RabbitMqMessageBus;
import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.performance.ThreadCountAutoscalingProtocol;
import org.eventrails.demo.DemoSagaApplication;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventRailsConfiguration {

	@Bean
	public EventRailsApplication eventRailsApplication(
			@Value("${eventrails.cluster.message.channel.name}") String channelName,
			@Value("${eventrails.cluster.node.server.name}") String serverName,
			@Value("${eventrails.cluster.bundle.name}") String bundleName,
			@Value("${eventrails.cluster.rabbitmq.host}") String rabbitHost,
			@Value("${eventrails.cluster.autoscaling.max.threads}") int maxThreads,
			@Value("${eventrails.cluster.autoscaling.max.overflow}") int maxOverflow,
			@Value("${eventrails.cluster.autoscaling.min.threads}") int minThreads,
			@Value("${eventrails.cluster.autoscaling.max.underflow}") int maxUnderflow,
			BeanFactory factory
	) throws Exception {

		MessageBus messageBus = RabbitMqMessageBus.create(bundleName, channelName, rabbitHost);
		return EventRailsApplication.start(DemoSagaApplication.class.getPackage().getName(),
				bundleName,
				serverName,
				messageBus,
				new ThreadCountAutoscalingProtocol(
						bundleName,
						serverName,
						messageBus,
						maxThreads,
						minThreads,
						maxOverflow,
						maxUnderflow),
				factory::getBean
		);
	}
}
