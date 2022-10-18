package org.eventrails.application.test;

import org.eventrails.shared.messaging.JGroupNodeAddress;
import org.eventrails.shared.messaging.JGroupsMessageBus;
import org.jgroups.JChannel;

import java.util.Scanner;

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
		messageBus.enableBus();

		var scanner = new Scanner(System.in);
		JGroupNodeAddress oldAddress = new JGroupNodeAddress(null);
		while (true){
			var command = scanner.next();
			if(command.equals("q")) break;
			try
			{
				var address = messageBus.findNodeAddress("client2");
				oldAddress = address;
				System.out.println("ADDRESS: " + address.getAddress());
				messageBus.cast(address, command);
			}catch (Exception e){
				e.printStackTrace();
				System.out.println("ADDRESS: " + oldAddress.getAddress());
				messageBus.cast(oldAddress, command);
			}
		}


	}
}
