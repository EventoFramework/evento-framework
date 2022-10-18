package org.eventrails.application.test;

import org.eventrails.shared.messaging.JGroupsMessageBus;
import org.jgroups.JChannel;

public class Client2 {
	public static void main(String[] args) throws Exception {
		var channel = new JChannel();
		var messageBus = new JGroupsMessageBus(channel);
		messageBus.setMessageReceiver(message -> {
					System.out.println("MESSAGE RECEIVED: " + message);
					if(message.equals("disconnect")){
						try
						{
							messageBus.disableBus();
						} catch (Exception e)
						{
							throw new RuntimeException(e);
						}
					} else if (message.equals("connect"))
					{
						try
						{
							messageBus.enableBus();
						} catch (Exception e)
						{
							throw new RuntimeException(e);
						}
					}
		});
		channel.setName("client2");
		channel.connect("cluster");
		messageBus.enableBus();

	}
}
