package org.evento.server.cluster;

import org.evento.bus.EventoMessageBus;
import org.evento.common.messaging.bus.MessageBus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@ConditionalOnProperty(prefix = "evento", name = "message.bus.type", havingValue = "evento")
public class EventoMessageBusConfiguration {

	@Value("${evento.cluster.message.channel.name}")
	private String handlerClusterName;

	@Value("${evento.cluster.node.server.id}")
	private String serverBundleId;

	@Value("${evento.cluster.node.server.version}")
	private long serverBundleVersion;

	@Value("${evento.message.bus.evento.host}")
	private String host;
	@Value("${evento.message.bus.evento.port}")
	private int port;

	@Bean
	@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
	MessageBus messageBus() throws Exception {
		var messageBus = EventoMessageBus.create(
				serverBundleId,
				serverBundleVersion,
				host,
				port
		);
		messageBus.enableBus();
		return messageBus;
	}

}
