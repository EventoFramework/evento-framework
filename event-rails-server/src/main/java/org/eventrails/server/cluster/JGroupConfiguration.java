package org.eventrails.server.cluster;

import org.eventrails.shared.messaging.JGroupsMessageBus;
import org.eventrails.modeling.messaging.message.bus.MessageBus;
import org.jgroups.Event;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.blocks.locking.LockService;
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
