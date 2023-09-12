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

	@Value("${evento.message.bus.rabbitmq.user}")
	private String rabbitUser;

	@Value("${evento.message.bus.rabbitmq.password}")
	private String rabbitPassword;

	@Value("${evento.message.bus.rabbitmq.timeout}")
	private int timeout;



	@Bean
	@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
	MessageBus messageBus() throws Exception {
		ConnectionFactory f = new ConnectionFactory();
		f.setHost(rabbitHost);
		f.setPassword(rabbitPassword);
		f.setUsername(rabbitUser);
		var messageBus = RabbitMqMessageBus.create(
				serverBundleId,
				serverBundleVersion,
				handlerClusterName,
				f, timeout
		);
		messageBus.enableBus();
		return messageBus;
	}

}
