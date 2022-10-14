package org.eventrails.application.test;

import org.eventrails.shared.messaging.JGroupsMessageBus;
import org.jgroups.JChannel;

public class Client1 {
	public static void main(String[] args) throws Exception {

		var channel = new JChannel();
		var messageBus = new JGroupsMessageBus(channel,
				message -> {
					System.out.println("MESSAGE RECEIVED: " + message);
				},
				((o, responseSender) -> {

				}));
		channel.setName("client1");
		channel.connect("cluster");

		messageBus.broadcast("Ciao Mondo!");
		messageBus.multicast(channel.getView().getMembers(), "Multicast Ciao Mondo");
		var channel2 = channel.getView().getMembers().stream().filter(a -> a.toString().equals("client2")).findFirst().orElseThrow();
		messageBus.cast(channel2,
				"Single Cast");
		messageBus.cast(channel2, "Concat this", resp -> {
			System.out.println("Response: " + resp);
		});


	}
}
