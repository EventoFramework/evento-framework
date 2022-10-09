package org.eventrails.server.cluster;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.blocks.RpcDispatcher;
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
	JChannel messageChannel() throws Exception {
		var jChannel = new JChannel();
		jChannel.setName(serverNodeName);
		jChannel.setReceiver(new Receiver() {
			@Override
			public void receive(Message msg) {
				System.out.println(msg);
			}
		});
		jChannel.connect(handlerClusterName);
		return jChannel;
	}

	@Bean
	RpcDispatcher dispatcher(JChannel messageChannel){
		var rpcDispatcher = new RpcDispatcher();
		rpcDispatcher.setChannel(messageChannel);
		rpcDispatcher.start();
		rpcDispatcher.wrapExceptions(true);
		return rpcDispatcher;
	}
	
}
