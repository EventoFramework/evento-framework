package org.eventrails.demo.web.domain.config;

import org.eventrails.application.EventRailsApplication;
import org.eventrails.bus.rabbitmq.RabbitMqMessageBus;
import org.eventrails.demo.web.domain.Application;
import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.messaging.gateway.CommandGateway;
import org.eventrails.common.messaging.gateway.QueryGateway;
import org.eventrails.common.performance.ThreadCountAutoscalingProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventRailsConfiguration {

	@Bean
	public EventRailsApplication eventRailsApplication(
			@Value("${eventrails.cluster.message.channel.name}") String channelName,
			@Value("${eventrails.cluster.node.server.name}") String serverName,
			@Value("${eventrails.cluster.bundle.name}") String bundleName
			) throws Exception {

		MessageBus messageBus = RabbitMqMessageBus.create(bundleName, channelName, "host.docker.internal");
		return EventRailsApplication.start(Application.class.getPackage().getName(),
				bundleName,
				serverName,
				messageBus,
				new ThreadCountAutoscalingProtocol(
						bundleName,
						serverName,
						messageBus,
						16,
						4,
						5,
						5));
	}

	@Bean
	public CommandGateway commandGateway(EventRailsApplication eventRailsApplication){
		return eventRailsApplication.getCommandGateway();
	}

	@Bean
	public QueryGateway queryGateway(EventRailsApplication eventRailsApplication){
		return eventRailsApplication.getQueryGateway();
	}
}
