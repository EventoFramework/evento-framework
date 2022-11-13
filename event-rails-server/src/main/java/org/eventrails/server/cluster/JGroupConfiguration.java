package org.eventrails.server.cluster;

import org.eventrails.bus.jgroups.JGroupsMessageBus;
import org.eventrails.common.messaging.bus.MessageBus;
import org.jgroups.JChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JGroupConfiguration {

	@Value("${eventrails.cluster.message.channel.name}")
	private String handlerClusterName;

	@Value("${eventrails.cluster.node.server.name}")
	private String serverNodeName;

	@Bean
	MessageBus messageBus() throws Exception {
		var jChannel = new JChannel();
		var messageBus = new JGroupsMessageBus(jChannel);
		jChannel.setName(serverNodeName);
		jChannel.setDiscardOwnMessages(false);
		jChannel.connect(handlerClusterName);
		messageBus.enableBus();
		return messageBus;
	}
	
}
