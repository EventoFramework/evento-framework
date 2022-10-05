package org.eventrails.server.cluster;

import org.jgroups.*;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestHandler;
import org.jgroups.blocks.Response;

import java.util.List;

public class JGroupCluster implements Receiver {

	private final JChannel channel;
	private final String channelName;
	private final String nodeName;

	private View lastView;

	public JGroupCluster(String channelName, String nodeName) throws Exception {
		this.channelName = channelName;
		this.nodeName = nodeName;
		this.channel = new JChannel();
		this.channel.setReceiver(this);

	}

	public void connect() throws Exception {
		channel.connect(channelName);
		channel.name(nodeName);
	}

	public void close(){
		channel.close();
	}

	@Override
	public void receive(Message msg) {


	}

	@Override
	public void viewAccepted(View newView) {
		if (lastView == null) {
			System.out.println("Received initial view:");
			newView.forEach(System.out::println);
		} else {
			System.out.println("Received new view.");

			List<Address> newMembers = View.newMembers(lastView, newView);
			System.out.println("New members: ");
			newMembers.forEach(System.out::println);

			List<Address> exMembers = View.leftMembers(lastView, newView);
			System.out.println("Exited members:");
			exMembers.forEach(System.out::println);
		}
		lastView = newView;
	}
}
