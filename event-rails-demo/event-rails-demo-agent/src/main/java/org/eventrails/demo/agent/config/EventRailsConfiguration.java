package org.eventrails.demo.agent.config;

import org.eventrails.application.EventRailsApplication;
import org.eventrails.bus.rabbitmq.RabbitMqMessageBus;
import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.performance.ThreadCountAutoscalingProtocol;
import org.eventrails.demo.agent.DemoAgentApplication;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
			@Value("${eventrails.bundle.autorun:false}") boolean autorun,
			@Value("${eventrails.bundle.instances.min:0}") int minInstances,
			@Value("${eventrails.bundle.instances.max:64}") int maxInstances,
			BeanFactory factory
	) throws Exception {
		MessageBus messageBus = RabbitMqMessageBus.create(bundleId, bundleVersion, channelName, rabbitHost);
		return EventRailsApplication.start(DemoAgentApplication.class.getPackage().getName(),
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
				factory::getBean
		);
	}
}