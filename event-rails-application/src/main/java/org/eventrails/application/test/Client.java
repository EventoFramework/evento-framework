package org.eventrails.application.test;

import org.jgroups.JChannel;
import org.jgroups.ObjectMessage;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.MethodCall;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.util.Util;

public class Client {

	public static void main(String[] args) throws Exception {
		var channel = new JChannel();
		channel.setName("client");
		channel.connect("cluster");

		var dispatcher = new MessageDispatcher(channel);

		System.out.println("invoke");
		var resp = dispatcher.sendMessageWithFuture(
				new ObjectMessage(
				channel.getView().getMembers().stream().filter(address -> address.toString().equals("gateway")).findFirst().orElseThrow(),
				"bolla"
		), RequestOptions.SYNC());
		System.out.println("done");
		System.out.println(resp.get());
		Util.close(dispatcher,channel);

	}
}
