package org.eventrails.dispatcher;

import org.jgroups.*;

import java.util.List;
import java.util.Scanner;

public class Application {
	public static void main(String[] args) throws Exception {
		var channel = new JChannel();
		channel.setName("event-rails-node-dispatcher");
		channel.setReceiver(new Receiver() {
			@Override
			public void receive(Message msg) {
				System.out.println("received message " + msg);
			}

			private View lastView;

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
		});
		channel.connect("event-rails-channel-message");

	}
}
