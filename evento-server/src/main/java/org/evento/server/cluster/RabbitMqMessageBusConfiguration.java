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
	private String exchange;

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

	@Value("${evento.message.bus.rabbitmq.disable.waiting.time:15000}")
	private Integer waitingTime;

	@Value("${evento.message.bus.rabbitmq.disable.max.retry:15}")
	private Integer maxRetry;


	@Bean
	@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
	MessageBus messageBus() throws Exception {
		ConnectionFactory f = new ConnectionFactory();
		f.setHost(rabbitHost);
		f.setPassword(rabbitPassword);
		f.setUsername(rabbitUser);
		var messageBus = RabbitMqMessageBus.Builder.builder()
				.setBundleId(serverBundleId)
				.setBundleVersion(serverBundleVersion)
				.setExchange(exchange)
				.setFactory(f)
				.setRequestTimeout(timeout)
				.setDisableMaxRetry(maxRetry)
				.setDisableWaitingTime(waitingTime)
				.connect();
		messageBus.enableBus();
		return messageBus;
	}

}
