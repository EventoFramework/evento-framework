package org.eventrails.shared.messaging;

import org.eventrails.modeling.exceptions.NodeNotFoundException;
import org.eventrails.modeling.exceptions.ThrowableWrapper;
import org.eventrails.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.modeling.messaging.message.bus.CorrelatedMessage;
import org.eventrails.modeling.messaging.message.bus.MessageBus;
import org.eventrails.modeling.messaging.message.bus.ResponseSender;
import org.jgroups.*;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class JGroupsMessageBus implements MessageBus, Receiver {

	private final JChannel channel;
	private Consumer<Object> messageReceiver;
	private BiConsumer<Object, ResponseSender> requestReceiver;

	private final HashMap<String, Handlers> messageCorrelationMap = new HashMap<>();

	public JGroupsMessageBus(JChannel jChannel,
							 Consumer<Object> messageReceiver,
							 BiConsumer<Object, ResponseSender> requestReceiver) {
		jChannel.setReceiver(this);
		this.channel = jChannel;
		this.messageReceiver = messageReceiver;
		this.requestReceiver = requestReceiver;
	}

	public JGroupsMessageBus(JChannel jChannel) {
		jChannel.setReceiver(this);
		this.channel = jChannel;
		this.messageReceiver = object -> {};
		this.requestReceiver = (request, response) -> {};
	}

	public void setMessageReceiver(Consumer<Object> messageReceiver) {
		this.messageReceiver = messageReceiver;
	}

	public void setRequestReceiver(BiConsumer<Object, ResponseSender> requestReceiver) {
		this.requestReceiver = requestReceiver;
	}

	@Override
	public void broadcast(Object message) throws Exception {
		channel.send(new BytesMessage(null, message));

	}

	@Override
	public void cast(NodeAddress address, Object message) throws Exception {
		channel.send(new BytesMessage(address.getAddress(), message));
	}

	public void cast(Address address, Object message) throws Exception {
		cast(new JGroupNodeAddress(address), message);
	}


	@Override
	public void multicast(Collection<NodeAddress> addresses, Object message) throws Exception {
		for (NodeAddress address : addresses)
		{
			cast(address, message);
		}
	}
	public void multicast(List<Address> addresses, Object message) throws Exception {
		multicast(addresses.stream().map(JGroupNodeAddress::new).collect(Collectors.toList()), message);
	}


	@Override
	public void cast(NodeAddress address, Object message, Consumer<Object> response, Consumer<ThrowableWrapper> error) throws Exception {
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

	public void cast(Address address, Object message, Consumer<Object> response, Consumer<ThrowableWrapper> error) throws Exception{
		cast(new JGroupNodeAddress(address), message, response, error);
	}

	public void cast(Address address, Object message, Consumer<Object> response) throws Exception{
		cast(new JGroupNodeAddress(address), message, response);
	}

	private void castResponse(NodeAddress address, Object message, String correlationId) throws Exception {
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
		Object message = ((BytesMessage) msg).getObject(this.getClass().getClassLoader());
		if (message instanceof CorrelatedMessage cm)
		{
			if (cm.isResponse())
			{
				if(cm.getPayload() instanceof ThrowableWrapper tw){
					messageCorrelationMap.get(cm.getCorrelationId()).fail.accept(tw);
				}else
				{
					messageCorrelationMap.get(cm.getCorrelationId()).success.accept(cm.getPayload());
				}
			} else
			{	var resp = new JGroupsResponseSender(
					this,
					new JGroupNodeAddress(msg.getSrc()),
					cm.getCorrelationId());
				try
				{
					requestReceiver.accept(cm.getPayload(), resp);
				}catch (Exception e){
					resp.sendError(e);
				}
			}
		}else
		{
			messageReceiver.accept(message);
		}
	}

	private static class Handlers{
		private Consumer<Object> success;
		private Consumer<ThrowableWrapper> fail;

		public Handlers(Consumer<Object> success, Consumer<ThrowableWrapper> fail) {
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

		public void sendResponse(Object response){
			if(responseSent) return;
			try
			{
				messageBus.castResponse(responseAddress, response, correlationId);
				responseSent = true;
			} catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		}

		public void sendError(Throwable e){
			if(responseSent) return;
			try
			{
				messageBus.castResponse(responseAddress,  new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace()), correlationId);
				responseSent = true;
			} catch (Exception err)
			{
				throw new RuntimeException(err);
			}
		}
	}


}
