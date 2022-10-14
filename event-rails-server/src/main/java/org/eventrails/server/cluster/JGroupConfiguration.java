package org.eventrails.server.cluster;

import org.eventrails.shared.messaging.JGroupsMessageBus;
import org.eventrails.modeling.messaging.message.bus.MessageBus;
import org.jgroups.Event;
import org.jgroups.JChannel;
import org.jgroups.Message;
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
		var jChannel = new JChannel(){
			@Override
			public Object up(Message msg) {
				System.out.println("UP MSG - " + msg);
				return super.up(msg);
			}

			@Override
			public Object up(Event evt) {
				System.out.println("UP EVT - " + evt);
				return super.up(evt);
			}



			@Override
			public Object down(Event evt) {
				System.out.println("DOWN EVT - " + evt);
				return super.down(evt);
			}

			@Override
			public Object down(Message evt) {
				System.out.println("DOWN MSG - " + evt);
				return super.down(evt);
			}
		};
		var messageBus = new JGroupsMessageBus(jChannel);
		jChannel.setName(serverNodeName);
		jChannel.setDiscardOwnMessages(false);
		jChannel.connect(handlerClusterName);
		return messageBus;
	}
	
}
