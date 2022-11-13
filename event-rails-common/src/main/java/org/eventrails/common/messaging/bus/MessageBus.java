package org.eventrails.common.messaging.bus;

import org.eventrails.common.modeling.exceptions.NodeNotFoundException;
import org.eventrails.common.modeling.exceptions.ThrowableWrapper;
import org.eventrails.common.modeling.messaging.message.bus.BusEventPublisher;
import org.eventrails.common.modeling.messaging.message.bus.BusEventSubscriber;
import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.common.modeling.messaging.message.bus.ResponseSender;
import org.eventrails.common.modeling.messaging.message.internal.ClusterNodeKillMessage;
import org.eventrails.common.modeling.messaging.message.internal.ClusterNodeStatusUpdateMessage;
import org.eventrails.common.modeling.messaging.message.internal.CorrelatedMessage;

import java.io.Serializable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public abstract class MessageBus {

	private final Logger LOGGER = Logger.getLogger(MessageBus.class.getName());
	private Consumer<Serializable> messageReceiver =  object -> {};
	private BiConsumer<Serializable, MessageBusResponseSender> requestReceiver = (request, response) -> {};

	private boolean enabled = false;

	private final HashMap<String, Handlers> messageCorrelationMap = new HashMap<>();
	private final HashSet<NodeAddress> availableNodes = new HashSet<>();
	private final List<Consumer<NodeAddress>> joinListeners = new ArrayList<>();
	private final List<Consumer<Set<NodeAddress>>> viewListeners = new ArrayList<>();
	private final List<Consumer<Set<NodeAddress>>> availableViewListeners = new ArrayList<>();


	public MessageBus(BusEventPublisher publisher) {
		publisher.subscribe(new BusEventSubscriber() {
			@Override
			public void onMessage(NodeAddress address, Serializable message) {
				receive(address, message);
			}
			@Override
			public void onViewUpdate(Set<NodeAddress> view) {
				availableNodes.removeIf(a -> !view.contains(a));
				viewListeners.stream().toList().forEach(l -> l.accept(view));
				availableViewListeners.stream().toList().forEach(l -> l.accept(availableNodes));
			}
		});
	}

	private record Handlers(Consumer<Serializable> success, Consumer<ThrowableWrapper> fail) {}

	public void setMessageReceiver(Consumer<Serializable> messageReceiver) {
		this.messageReceiver = messageReceiver;
	}

	public void setRequestReceiver(BiConsumer<Serializable, MessageBusResponseSender> requestReceiver) {
		this.requestReceiver = requestReceiver;
	}



	/**
	 * Send a message to an address
	 * @param address the destination address
	 * @param message the message to send
	 */
	public abstract void cast(NodeAddress address, Serializable message) throws Exception;

	/**
	 * Broadcast a message
	 * @param message the message to send
	 */
	public abstract void broadcast(Serializable message) throws Exception;


	/**
	 * Send a message to multiple addresses
	 * @param addresses receiver list
	 * @param message the message to send
	 */
	public void multicast(Collection<NodeAddress> addresses, Serializable message) throws Exception {
		for (NodeAddress address : addresses)
		{
			cast(address, message);
		}
	}

	/**
	 * Cast a message and wait for a response
	 * @param address the destination address
	 * @param message the message to send
	 * @param responseHandler the response handler
	 */
	public final void cast(NodeAddress address, Serializable message, Consumer<Serializable> responseHandler, Consumer<ThrowableWrapper> errorHandler) throws Exception {
		var correlationId = UUID.randomUUID().toString();
		messageCorrelationMap.put(correlationId, new Handlers(responseHandler, errorHandler));
		var cm = new CorrelatedMessage(correlationId, message, false);
		try
		{
			cast(address, cm);
		} catch (Exception e)
		{
			messageCorrelationMap.remove(correlationId);
			throw e;
		}
	}

	public void cast(NodeAddress address, Serializable message, Consumer<Serializable> responseHandler) throws Exception {
		this.cast(address, message, responseHandler, error -> {});
	}

	public NodeAddress findNodeAddress(String nodeName){
		return getCurrentAvailableView().stream()
				.filter(address -> nodeName.equals(address.getNodeName()))
				.findAny().orElseThrow(() -> new NodeNotFoundException("Node %s not found".formatted(nodeName)));
	}

	public abstract NodeAddress getAddress();

	public Set<NodeAddress> findAllNodeAddresses(String nodeName){
		return getCurrentAvailableView().stream()
				.filter(address -> nodeName.equals(address.getNodeName()))
				.collect(Collectors.toSet());
	}

	public void enableBus() throws Exception {
		this.broadcast(new ClusterNodeStatusUpdateMessage(true));
		this.enabled = true;
	}

	public void disableBus() throws Exception {
		this.broadcast(new ClusterNodeStatusUpdateMessage(false));
		this.enabled = false;
	}

	public synchronized void gracefulShutdown() {
		try
		{
			LOGGER.info("Graceful Shutdown - Started");
			LOGGER.info("Graceful Shutdown - Disabling Bus");
			disableBus();
			LOGGER.info("Graceful Shutdown - Bus Disabled");
			var retry = 0;
			while (true)
			{
				var keys = messageCorrelationMap.keySet();
				LOGGER.info("Graceful Shutdown - Remaining correlations: %d".formatted(keys.size()));
				LOGGER.info("Graceful Shutdown - Sleep...");
				Thread.sleep(5*1000);
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



	public void addJoinListener(Consumer<NodeAddress> onBundleJoin) {
		this.joinListeners.add(onBundleJoin);
	}

	public void removeJoinListener(Consumer<NodeAddress> onBundleJoin) {
		this.joinListeners.remove(onBundleJoin);
	}

	public void addViewListener(Consumer<Set<NodeAddress>> listener) {
		viewListeners.add(listener);
	}

	public void removeViewListener(Consumer<Set<NodeAddress>> listener) {
		viewListeners.remove(listener);
	}


	public void removeAvailableViewListener(Consumer<Set<NodeAddress>> listener) {
		availableViewListeners.remove(listener);
	}

	public void addAvailableViewListener(Consumer<Set<NodeAddress>> listener) {
		availableViewListeners.add(listener);
	}

	public HashSet<NodeAddress> getCurrentAvailableView() {
		return availableNodes;
	}

	public void sendKill(String nodeId) throws Exception {
		var nodeAddress = getCurrentView().stream().filter(a -> a.getNodeId().equals(nodeId)).findFirst().orElseThrow();
		cast(nodeAddress, new ClusterNodeKillMessage());
	}

	public boolean isBundleAvailable(String bundleName) {
		return getCurrentView().stream()
				.filter(a -> a.getNodeName().equals(bundleName))
				.anyMatch(this.availableNodes::contains);
	}



	private void receive(NodeAddress src, Serializable message) {
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
					var resp = new MessageBusResponseSender(
							this,
							src,
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
					if(!this.availableNodes.contains(src))
					{
						this.availableNodes.add(src);
						try
						{
							cast(src, new ClusterNodeStatusUpdateMessage(this.enabled));
						} catch (Exception e)
						{
							e.printStackTrace();
						}
						joinListeners.stream().toList().forEach(c -> c.accept(src));
					}

				}else{
					this.availableNodes.remove(src);
				}
				availableViewListeners.stream().toList().forEach(l -> l.accept(availableNodes));
			} else if (message instanceof ClusterNodeKillMessage k)
			{
				LOGGER.info("ClusterNodeKillMessage received from %s".formatted(src));
				gracefulShutdown();
			} else
			{
				messageReceiver.accept(message);
			}
		}).start();
	}

	private void castResponse(NodeAddress address, Serializable message, String correlationId) throws Exception {
		var cm = new CorrelatedMessage(correlationId, message, true);
		cast(address, cm);
	}

	public class MessageBusResponseSender implements ResponseSender {

		private final MessageBus messageBus;

		private final NodeAddress responseAddress;
		private final String correlationId;

		private boolean responseSent = false;

		private MessageBusResponseSender(MessageBus messageBus, NodeAddress responseAddress, String correlationId) {
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



	public abstract Set<NodeAddress>  getCurrentView();


}
