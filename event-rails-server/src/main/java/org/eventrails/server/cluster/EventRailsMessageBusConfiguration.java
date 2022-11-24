package org.eventrails.server.cluster;

import org.eventrails.bus.EventRailsMessageBus;
import org.eventrails.bus.rabbitmq.RabbitMqMessageBus;
import org.eventrails.common.messaging.bus.MessageBus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "eventrails", name = "message.bus.type", havingValue = "eventrails")
public class EventRailsMessageBusConfiguration {

	@Value("${eventrails.cluster.message.channel.name}")
	private String handlerClusterName;

	@Value("${eventrails.cluster.node.server.id}")
	private String serverBundleId;

	@Value("${eventrails.cluster.node.server.version}")
	private long serverBundleVersion;

	@Value("${eventrails.message.bus.eventrails.host}")
	private String host;
	@Value("${eventrails.message.bus.eventrails.port}")
	private int port;

	@Bean
	MessageBus messageBus() throws Exception {
		var messageBus = EventRailsMessageBus.create(
				serverBundleId,
				serverBundleVersion,
				host,
				port
				);
		messageBus.enableBus();
		return messageBus;
	}
	
}
