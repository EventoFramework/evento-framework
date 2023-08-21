package org.evento.server.cluster;

import com.rabbitmq.client.ConnectionFactory;
import org.evento.bus.rabbitmq.RabbitMqMessageBus;
import org.evento.common.messaging.bus.MessageBus;
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

	@Value("${evento.message.bus.rabbitmq.token}")
	private String rabbitToken;

	@Bean
	@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
	MessageBus messageBus() throws Exception {
		ConnectionFactory f = new ConnectionFactory();
		f.setHost(rabbitHost);
		f.setPassword(rabbitToken);
		f.setUsername("token");
		var messageBus = RabbitMqMessageBus.create(
				serverBundleId,
				serverBundleVersion,
				handlerClusterName,
				f, 10
		);
		messageBus.enableBus();
		return messageBus;
	}

}
