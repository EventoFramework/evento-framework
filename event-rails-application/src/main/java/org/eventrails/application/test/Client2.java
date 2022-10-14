package org.eventrails.application.test;

import org.eventrails.shared.messaging.JGroupsMessageBus;
import org.jgroups.JChannel;

public class Client2 {
	public static void main(String[] args) throws Exception {
		var channel = new JChannel();
		var messageBus = new JGroupsMessageBus(channel,
				message -> {
					System.out.println("MESSAGE RECEIVED: " + message);
				},
				(request, response) -> {
					response.sendResponse("REQUEST RECEIVED: " + request + ".... hmmm... ok");
				});
		channel.setName("client2");
		channel.connect("cluster");

	}
}
