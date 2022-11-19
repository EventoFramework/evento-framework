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

public class JGroupsMessageBus extends MessageBus implements Receiver {

	private final Log LOGGER = LogFactory.getLog(JGroupsMessageBus.class);
	private final JChannel channel;
	private long bundleVersion;

	protected JGroupsMessageBus(
			JChannel jChannel,
			long bundleVersion,
			Consumer<Serializable> messageReceiver,
			BiConsumer<Serializable, MessageBusResponseSender> requestReceiver) {

		super(subscriber -> jChannel.setReceiver(new Receiver() {

			private Set<NodeAddress> ov = new HashSet<>();

			@Override
			public void receive(Message msg) {
				subscriber.onMessage(new JGroupNodeAddress(msg.getSrc(), bundleVersion), ((BytesMessage) msg).getObject(this.getClass().getClassLoader()));
			}

			@Override
			public synchronized void viewAccepted(View newView) {
				Set<NodeAddress> nv = newView.getMembers().stream().map(a -> new JGroupNodeAddress(a, bundleVersion)).collect(Collectors.toSet());
				subscriber.onViewUpdate(nv, nv.stream().filter(n -> !ov.contains(n)).collect(Collectors.toSet()), ov.stream().filter(n -> !nv.contains(n)).collect(Collectors.toSet()));
				ov = nv;
			}
		}));
		this.channel = jChannel;
		this.bundleVersion = bundleVersion;
		setMessageReceiver(messageReceiver);
		setRequestReceiver(requestReceiver);
	}

	protected JGroupsMessageBus(JChannel jChannel, long bundleVersion) {
		super(subscriber -> jChannel.setReceiver(new Receiver() {

			private Set<NodeAddress> ov = new HashSet<>();

			@Override
			public void receive(Message msg) {
				subscriber.onMessage(new JGroupNodeAddress(msg.getSrc(), bundleVersion), ((BytesMessage) msg).getObject(this.getClass().getClassLoader()));
			}

			@Override
			public synchronized void viewAccepted(View newView) {
				Set<NodeAddress> nv = newView.getMembers().stream().map(a -> new JGroupNodeAddress(a, bundleVersion)).collect(Collectors.toSet());
				subscriber.onViewUpdate(nv, nv.stream().filter(n -> !ov.contains(n)).collect(Collectors.toSet()), ov.stream().filter(n -> !nv.contains(n)).collect(Collectors.toSet()));
				ov = nv;
			}
		}));
		this.channel = jChannel;
	}

	public static MessageBus create(
			String bundleId,
			long bundleVersion,
			String channelName) throws Exception {
		var jChannel = new JChannel();
		jChannel.setName(bundleId);
		var bus = new JGroupsMessageBus(jChannel, bundleVersion);
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
		return new JGroupNodeAddress(channel.getAddress(), bundleVersion);
	}

	@Override
	public Set<NodeAddress> getCurrentView() {
		return channel.getView().getMembers().stream().map(a -> new JGroupNodeAddress(a, bundleVersion)).collect(Collectors.toSet());
	}


}
