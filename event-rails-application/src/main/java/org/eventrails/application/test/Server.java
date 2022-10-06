package org.eventrails.application.test;

import org.jgroups.JChannel;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RpcDispatcher;

public class Server {

	public static void main(String[] args) throws Exception {
		var channel = new JChannel();
		channel.setName("server");
		channel.connect("cluster");

		var service = new ServiceImpl();

		var dispatcher = new RpcDispatcher(channel,service);
		dispatcher.start();


	}
}
