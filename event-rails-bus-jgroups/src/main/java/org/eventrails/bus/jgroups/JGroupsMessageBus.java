package org.eventrails.bus.jgroups;

import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.common.messaging.bus.MessageBus;
import org.jgroups.*;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;

import java.io.Serializable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class JGroupsMessageBus extends MessageBus implements Receiver{

	private final Log LOGGER = LogFactory.getLog(JGroupsMessageBus.class);
	private final JChannel channel;

	public JGroupsMessageBus(JChannel jChannel,
							 Consumer<Serializable> messageReceiver,
							 BiConsumer<Serializable, MessageBusResponseSender> requestReceiver) {

		super(subscriber -> jChannel.setReceiver(new Receiver() {
			@Override
			public void receive(Message msg) {
				subscriber.onMessage(new JGroupNodeAddress(msg.getSrc()), ((BytesMessage) msg).getObject(this.getClass().getClassLoader()));
			}
			@Override
			public void viewAccepted(View newView) {
				subscriber.onViewUpdate(newView.getMembers().stream().map(JGroupNodeAddress::new).collect(Collectors.toSet()));
			}
		}));
		this.channel = jChannel;
		setMessageReceiver(messageReceiver);
		setRequestReceiver(requestReceiver);
	}

	public JGroupsMessageBus(JChannel jChannel) {
		super(subscriber -> jChannel.setReceiver(new Receiver() {
			@Override
			public void receive(Message msg) {
				subscriber.onMessage(new JGroupNodeAddress(msg.getSrc()), ((BytesMessage) msg).getObject(this.getClass().getClassLoader()));
			}
			@Override
			public void viewAccepted(View newView) {
				subscriber.onViewUpdate(newView.getMembers().stream().map(JGroupNodeAddress::new).collect(Collectors.toSet()));
			}
		}));
		this.channel = jChannel;
	}

	public static MessageBus create(
			String bundleName,
			String channelName) throws Exception {
		var jChannel = new JChannel("docker-local.xml");
		jChannel.setName(bundleName);
		var bus = new JGroupsMessageBus(jChannel);
		jChannel.connect(channelName);
		return bus;
	}


	@Override
	public void broadcast(Serializable message) throws Exception {
		channel.send(new BytesMessage(null, message));

	}

	@Override
	public void cast(NodeAddress address, Serializable message) throws Exception {
		channel.send(new BytesMessage(address.getAddress(), message));
	}


	@Override
	public NodeAddress getAddress() {
		return new JGroupNodeAddress(channel.getAddress());
	}

	@Override
	public Set<NodeAddress> getCurrentView() {
		return channel.getView().getMembers().stream().map(JGroupNodeAddress::new).collect(Collectors.toSet());
	}


}
