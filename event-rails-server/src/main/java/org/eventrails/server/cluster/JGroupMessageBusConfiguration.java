package org.eventrails.server.cluster;

import org.eventrails.bus.jgroups.JGroupsMessageBus;
import org.eventrails.common.messaging.bus.MessageBus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "eventrails", name = "message.bus.type", havingValue = "jgroups")
public class JGroupMessageBusConfiguration {

	@Value("${eventrails.cluster.message.channel.name}")
	private String handlerClusterName;

	@Value("${eventrails.cluster.node.server.id}")
	private String serverBundleId;

	@Value("${eventrails.cluster.node.server.version}")
	private long serverBundleVersion;

	@Bean
	MessageBus messageBus() throws Exception {
		var messageBus = JGroupsMessageBus.create(
				serverBundleId,
				serverBundleVersion,
				handlerClusterName);
		messageBus.enableBus();
		return messageBus;
	}
	
}
