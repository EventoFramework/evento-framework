package org.eventrails.application.test;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.blocks.*;

public class Gateway {
	public static void main(String[] args) throws Exception {

		var rpcChannel = new JChannel();
		rpcChannel.setName("gateway");
		rpcChannel.connect("cluster");

		var rpc = new RpcDispatcher(rpcChannel, null);


		rpc.setRequestHandler(new RequestHandler() {
			@Override
			public Object handle(Message msg) throws Exception {
				System.out.println("gateway handler");
				System.out.println(msg);
				return rpc.callRemoteMethod(
						rpc.getChannel().getView().getMembers().stream().filter(address -> address.toString().equals("server")).findFirst().orElseThrow(),
						new MethodCall(Service.class.getMethod("concat", String.class, String.class), "start+", msg.getPayload()),
						RequestOptions.SYNC()
				);
			}
		});


	}
}
