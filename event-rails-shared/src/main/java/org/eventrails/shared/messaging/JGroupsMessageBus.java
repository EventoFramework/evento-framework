package org.eventrails.shared.messaging;

import org.eventrails.modeling.exceptions.NodeNotFoundException;
import org.eventrails.modeling.exceptions.ThrowableWrapper;
import org.eventrails.modeling.messaging.message.bus.*;
import org.jgroups.*;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;

import java.io.Serializable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class JGroupsMessageBus implements MessageBus, Receiver {


	private final Log LOGGER = LogFactory.getLog(JGroupsMessageBus.class);


	private final JChannel channel;
	private Consumer<Serializable> messageReceiver;
	private BiConsumer<Serializable, ResponseSender> requestReceiver;

	private final HashMap<String, Handlers> messageCorrelationMap = new HashMap<>();
	private final HashSet<Address> availableNodes = new HashSet<>();


	private boolean enabled = false;
	private boolean isShuttingDown = false;
	private final List<Consumer<String>> joinListeners = new ArrayList<>();
	private final List<Consumer<List<NodeAddress>>> viewListeners = new ArrayList<>();
	private final List<Consumer<List<NodeAddress>>> availableViewListeners = new ArrayList<>();

	public JGroupsMessageBus(JChannel jChannel,
							 Consumer<Serializable> messageReceiver,
							 BiConsumer<Serializable, ResponseSender> requestReceiver) {
		jChannel.setReceiver(this);
		this.channel = jChannel;
		this.messageReceiver = messageReceiver;
		this.requestReceiver = requestReceiver;
	}

	public void enableBus() throws Exception {
		LOGGER.info("BUS ENABLED");
		this.broadcast(new ClusterNodeStatusUpdateMessage(true));
		this.enabled = true;
	}

	public void disableBus() throws Exception {
		LOGGER.info("BUS DISABLED");
		this.broadcast(new ClusterNodeStatusUpdateMessage(false));
		this.enabled = false;
	}

	@Override
	public synchronized void gracefulShutdown() {
		try
		{
			LOGGER.info("Graceful Shutdown - Started");
			this.isShuttingDown = true;
			LOGGER.info("Graceful Shutdown - Disabling Bus");
			disableBus();
			LOGGER.info("Graceful Shutdown - Bus Disabled");
			var retry = 0;
			while (true)
			{
				var keys = messageCorrelationMap.keySet();
				LOGGER.info("Graceful Shutdown - Remaining correlations: %d", keys.size());
				LOGGER.info("Graceful Shutdown - Sleep...");
				Thread.sleep(1000);
				if (messageCorrelationMap.isEmpty())
				{
					LOGGER.info("Graceful Shutdown - No more correlations, bye!");
					System.exit(0);
				} else if (keys.containsAll(messageCorrelationMap.keySet()) && retry > 5)
				{
					LOGGER.info("Graceful Shutdown - Pending correlation after 5 retry... so... bye!");
					System.exit(0);
				}
				retry++;
			}
		}catch (Exception e){
			e.printStackTrace();
			System.exit(1);
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public boolean isRanchAvailable(String ranchName) {
		return channel.getView().getMembers().stream()
				.filter(address -> ranchName.equals(address.toString()))
				.anyMatch(this.availableNodes::contains);
	}

	@Override
	public void addJoinListener(Consumer<String> onRanchJoin) {
		this.joinListeners.add(onRanchJoin);
	}

	@Override
	public void removeJoinListener(Consumer<String> onRanchJoin) {
		this.joinListeners.remove(onRanchJoin);
	}

	@Override
	public void addViewListener(Consumer<List<NodeAddress>> listener) {
		viewListeners.add(listener);
	}

	@Override
	public void removeViewListener(Consumer<List<NodeAddress>> listener) {
		viewListeners.remove(listener);
	}

	@Override
	public void removeAvailableViewListener(Consumer<List<NodeAddress>> listener) {
		availableViewListeners.remove(listener);
	}

	@Override
	public void addAvailableViewListener(Consumer<List<NodeAddress>> listener) {
		availableViewListeners.add(listener);
	}

	@Override
	public List<NodeAddress> getCurrentAvailableView() {
		return availableNodes.stream().map(JGroupNodeAddress::new).collect(Collectors.toList());
	}

	@Override
	public List<NodeAddress> getCurrentView() {
		return channel.getView().getMembers().stream().map(JGroupNodeAddress::new).collect(Collectors.toList());
	}

	@Override
	public void sendKill(String nodeId) throws Exception {
		Address nodeAddress = org.jgroups.util.UUID.fromString(nodeId);
		cast(nodeAddress, new ClusterNodeKillMessage());
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
	public NodeAddress getAddress() {
		return new JGroupNodeAddress(channel.getAddress());
	}

	@Override
	public void receive(Message msg) {
		Serializable message = ((BytesMessage) msg).getObject(this.getClass().getClassLoader());
		new Thread(() -> {
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
					messageCorrelationMap.remove(cm.getCorrelationId());
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
			} else if (message instanceof ClusterNodeStatusUpdateMessage u)
			{
				if(u.getNewStatus()){
					if(!this.availableNodes.contains(msg.getSrc()))
					{
						this.availableNodes.add(msg.getSrc());
						try
						{
							cast(msg.src(), new ClusterNodeStatusUpdateMessage(this.enabled));
						} catch (Exception e)
						{
							e.printStackTrace();
						}
						joinListeners.stream().toList().forEach(c -> c.accept(msg.getSrc().toString()));
					}

				}else{
					this.availableNodes.remove(msg.getSrc());
				}
				availableViewListeners.stream().toList().forEach(l -> l.accept(availableNodes.stream().map(JGroupNodeAddress::new).collect(Collectors.toList())));
			} else if (message instanceof ClusterNodeKillMessage k)
			{
				LOGGER.info("ClusterNodeKillMessage received from %s", msg.getSrc());
				gracefulShutdown();
			} else
			{
				messageReceiver.accept(message);
			}
		}).start();
	}

	@Override
	public void viewAccepted(View newView) {
		this.availableNodes.removeIf(a -> !newView.getMembers().contains(a));
		viewListeners.stream().toList().forEach(l -> l.accept(newView.getMembers().stream().map(JGroupNodeAddress::new).collect(Collectors.toList())));
		availableViewListeners.stream().toList().forEach(l -> l.accept(availableNodes.stream().map(JGroupNodeAddress::new).collect(Collectors.toList())));
	}

	@Override
	public JGroupNodeAddress findNodeAddress(String nodeName) {
		return channel.getView().getMembers().stream()
				.filter(address -> nodeName.equals(address.toString()))
				.filter(this.availableNodes::contains)
				.findAny().map(JGroupNodeAddress::new).orElseThrow(() -> new NodeNotFoundException("Node %s not found".formatted(nodeName)));
	}

	@Override
	public List<NodeAddress> findAllNodeAddresses(String nodeName) {
		return new ArrayList<>(channel.getView().getMembers().stream()
				.filter(address -> nodeName.equals(address.toString()))
				.filter(this.availableNodes::contains)
				.map(JGroupNodeAddress::new)
				.sorted()
				.toList());
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
