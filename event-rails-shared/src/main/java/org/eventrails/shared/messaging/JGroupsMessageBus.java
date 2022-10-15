package org.eventrails.shared.messaging;

import org.eventrails.modeling.exceptions.NodeNotFoundException;
import org.eventrails.modeling.exceptions.ThrowableWrapper;
import org.eventrails.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.modeling.messaging.message.bus.CorrelatedMessage;
import org.eventrails.modeling.messaging.message.bus.MessageBus;
import org.eventrails.modeling.messaging.message.bus.ResponseSender;
import org.jgroups.*;
import org.jgroups.util.ConcurrentLinkedBlockingQueue;
import org.jgroups.util.ConcurrentLinkedBlockingQueue2;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class JGroupsMessageBus implements MessageBus, Receiver {

	private final JChannel channel;
	private Consumer<Serializable> messageReceiver;
	private BiConsumer<Serializable, ResponseSender> requestReceiver;

	private final HashMap<String, Handlers> messageCorrelationMap = new HashMap<>();

	private final Queue<Message> messageQueue = new LinkedBlockingQueue<>();

	public JGroupsMessageBus(JChannel jChannel,
							 Consumer<Serializable> messageReceiver,
							 BiConsumer<Serializable, ResponseSender> requestReceiver) {
		jChannel.setReceiver(this);
		this.channel = jChannel;
		this.messageReceiver = messageReceiver;
		this.requestReceiver = requestReceiver;
	}

	public JGroupsMessageBus(JChannel jChannel) {
		jChannel.setReceiver(this);
		this.channel = jChannel;
		this.messageReceiver = object -> {
		};
		this.requestReceiver = (request, response) -> {
		};
	}

	public void setMessageReceiver(Consumer<Serializable> messageReceiver) {
		this.messageReceiver = messageReceiver;
	}

	public void setRequestReceiver(BiConsumer<Serializable, ResponseSender> requestReceiver) {
		this.requestReceiver = requestReceiver;
	}

	@Override
	public void broadcast(Serializable message) throws Exception {
		channel.send(new BytesMessage(null, message));

	}

	@Override
	public void cast(NodeAddress address, Serializable message) throws Exception {
		channel.send(new BytesMessage(address.getAddress(), message));
	}

	public void cast(Address address, Serializable message) throws Exception {
		cast(new JGroupNodeAddress(address), message);
	}


	@Override
	public void multicast(Collection<NodeAddress> addresses, Serializable message) throws Exception {
		for (NodeAddress address : addresses)
		{
			cast(address, message);
		}
	}

	public void multicast(List<Address> addresses, Serializable message) throws Exception {
		multicast(addresses.stream().map(JGroupNodeAddress::new).collect(Collectors.toList()), message);
	}


	@Override
	public void cast(NodeAddress address, Serializable message, Consumer<Serializable> response, Consumer<ThrowableWrapper> error) throws Exception {
		var correlationId = UUID.randomUUID().toString();
		messageCorrelationMap.put(correlationId, new Handlers(response, error));
		var cm = new CorrelatedMessage(correlationId, message, false);
		var jMessage = new BytesMessage(address.getAddress(), cm);
		try
		{
			channel.send(jMessage);
		} catch (Exception e)
		{
			messageCorrelationMap.remove(correlationId);
			throw e;
		}
	}

	public void cast(Address address, Serializable message, Consumer<Serializable> response, Consumer<ThrowableWrapper> error) throws Exception {
		cast(new JGroupNodeAddress(address), message, response, error);
	}

	public void cast(Address address, Serializable message, Consumer<Serializable> response) throws Exception {
		cast(new JGroupNodeAddress(address), message, response);
	}

	private void castResponse(NodeAddress address, Serializable message, String correlationId) throws Exception {
		var cm = new CorrelatedMessage(correlationId, message, true);
		var jMessage = new BytesMessage(address.getAddress(), cm);
		channel.send(jMessage);
	}

	@Override
	public JGroupNodeAddress findNodeAddress(String serverName) {
		return channel.getView().getMembers().stream()
				.filter(address -> serverName.equals(address.toString()))
				.findAny().map(JGroupNodeAddress::new).orElseThrow(() -> new NodeNotFoundException("Node %s not found".formatted(serverName)));
	}

	@Override
	public NodeAddress getAddress() {
		return new JGroupNodeAddress(channel.getAddress());
	}

	@Override
	public List<NodeAddress> getAddresses(String serverNodeName) {
		return new ArrayList<>(channel.getView().getMembers().stream()
				.filter(address -> serverNodeName.equals(address.toString()))
				.map(JGroupNodeAddress::new)
				.sorted()
				.toList());
	}


	@Override
	public void receive(Message msg) {
		Serializable message = ((BytesMessage) msg).getObject(this.getClass().getClassLoader());
		if (message instanceof CorrelatedMessage cm)
		{
			if (cm.isResponse())
			{
				if (cm.getBody() instanceof ThrowableWrapper tw)
				{
					messageCorrelationMap.get(cm.getCorrelationId()).fail.accept(tw);
				} else
				{
					try
					{
						messageCorrelationMap.get(cm.getCorrelationId()).success.accept(cm.getBody());
					} catch (Exception e)
					{
						e.printStackTrace();
						messageCorrelationMap.get(cm.getCorrelationId()).fail.accept(new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace()));
					}
				}
			} else
			{
				var resp = new JGroupsResponseSender(
						this,
						new JGroupNodeAddress(msg.getSrc()),
						cm.getCorrelationId());
				try
				{
					requestReceiver.accept(cm.getBody(), resp);
				} catch (Exception e)
				{
					e.printStackTrace();
					resp.sendError(e);
				}
			}
		} else
		{
			messageReceiver.accept(message);
		}

	}

	private static class Handlers {
		private Consumer<Serializable> success;
		private Consumer<ThrowableWrapper> fail;

		public Handlers(Consumer<Serializable> success, Consumer<ThrowableWrapper> fail) {
			this.success = success;
			this.fail = fail;
		}
	}

	public static class JGroupsResponseSender extends ResponseSender {


		private final JGroupsMessageBus messageBus;

		private final NodeAddress responseAddress;
		private final String correlationId;

		private boolean responseSent = false;

		private JGroupsResponseSender(JGroupsMessageBus messageBus, NodeAddress responseAddress, String correlationId) {
			this.messageBus = messageBus;
			this.responseAddress = responseAddress;
			this.correlationId = correlationId;
		}

		public void sendResponse(Serializable response) {
			if (responseSent) return;
			try
			{
				messageBus.castResponse(responseAddress, response, correlationId);
				responseSent = true;
			} catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		}

		public void sendError(Throwable e) {
			if (responseSent) return;
			try
			{
				messageBus.castResponse(responseAddress, new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace()), correlationId);
				responseSent = true;
			} catch (Exception err)
			{
				throw new RuntimeException(err);
			}
		}
	}


}
