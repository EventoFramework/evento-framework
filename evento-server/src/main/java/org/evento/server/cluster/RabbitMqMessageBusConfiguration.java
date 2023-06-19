package org.evento.server.cluster;

import org.evento.bus.rabbitmq.RabbitMqMessageBus;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.serialization.ObjectMapperUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@ConditionalOnProperty(prefix = "evento", name = "message.bus.type", havingValue = "rabbitmq")
public class RabbitMqMessageBusConfiguration {

	@Value("${evento.cluster.message.channel.name}")
	private String handlerClusterName;

	@Value("${evento.cluster.node.server.id}")
	private String serverBundleId;

	@Value("${evento.cluster.node.server.version}")
	private long serverBundleVersion;


	@Value("${evento.message.bus.rabbitmq.host}")
	private String rabbitHost;

	@Bean
	@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
	MessageBus messageBus() throws Exception {
		var messageBus = RabbitMqMessageBus.create(
				serverBundleId,
				serverBundleVersion,
				handlerClusterName,
				rabbitHost, 10
		);
		messageBus.enableBus();
		return messageBus;
	}

}
