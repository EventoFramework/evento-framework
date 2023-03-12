package org.evento.common.messaging.bus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.TriConsumer;
import org.evento.common.modeling.exceptions.ThrowableWrapper;
import org.evento.common.modeling.messaging.message.bus.BusEventPublisher;
import org.evento.common.modeling.messaging.message.bus.BusEventSubscriber;
import org.evento.common.modeling.messaging.message.bus.NodeAddress;
import org.evento.common.modeling.messaging.message.bus.ResponseSender;
import org.evento.common.modeling.messaging.message.internal.ClusterNodeKillMessage;
import org.evento.common.modeling.messaging.message.internal.ClusterNodeStatusUpdateMessage;
import org.evento.common.modeling.messaging.message.internal.CorrelatedMessage;
import org.evento.common.modeling.exceptions.NodeNotFoundException;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class MessageBus {

	private static Logger logger = LogManager.getLogger(MessageBus.class);
	private BiConsumer<NodeAddress, Serializable> messageReceiver = (address, object) -> {
	};
	private TriConsumer<NodeAddress, Serializable, MessageBusResponseSender> requestReceiver = (address, request, response) -> {
	};

	private boolean enabled = false;

	private final ConcurrentHashMap<String, Handlers> messageCorrelationMap = new ConcurrentHashMap<>();
	private final HashSet<NodeAddress> availableNodes = new HashSet<>();
	private final List<Consumer<NodeAddress>> joinListeners = new ArrayList<>();
	private final List<Consumer<NodeAddress>> leaveListeners = new ArrayList<>();
	private final List<Consumer<Set<NodeAddress>>> viewListeners = new ArrayList<>();
	private final List<Consumer<Set<NodeAddress>>> availableViewListeners = new ArrayList<>();
	private Set<NodeAddress> currentView = new HashSet<>();


	protected MessageBus(BusEventPublisher publisher) {
		publisher.subscribe(new BusEventSubscriber() {
			@Override
			public void onMessage(NodeAddress address, Serializable message) {
				receive(address, message);
			}

			@Override
			public void onViewUpdate(Set<NodeAddress> view, Set<NodeAddress> nodeAdded, Set<NodeAddress> nodeRemoved) {
				logger.info("View Updated - {}", view.stream().map(NodeAddress::getNodeId).collect(Collectors.joining(" ")));
				currentView = view;
				availableNodes.removeAll(nodeRemoved);
				viewListeners.stream().toList().forEach(l -> l.accept(view));
				if (nodeRemoved.size() > 0)
				{
					availableViewListeners.stream().toList().forEach(l -> l.accept(availableNodes));
					for (NodeAddress nodeAddress : nodeRemoved)
					{
						leaveListeners.stream().toList().forEach(l -> l.accept(nodeAddress));
					}
				}
			}
		});

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try
			{
				System.out.println("Graceful Shutdown - Started");
				System.out.println("Graceful Shutdown - Disabling Bus");
				disableBus();
				System.out.println("Waiting for bus disabled propagation...");
				Thread.sleep(15 * 1000);
				System.out.println("Graceful Shutdown - Bus Disabled");
				var retry = 0;
				while (true)
				{
					var keys = messageCorrelationMap.keySet();
					System.out.println("Graceful Shutdown - Remaining correlations: %d".formatted(keys.size()));
					System.out.println("Graceful Shutdown - Sleep...");
					Thread.sleep(15 * 1000);
					if (messageCorrelationMap.isEmpty())
					{
						System.out.println("Graceful Shutdown - No more correlations, bye!");
						disconnect();
						return;
					} else if (keys.containsAll(messageCorrelationMap.keySet()) && retry > 12)
					{
						System.out.println("Graceful Shutdown - Pending correlation after 180 sec of retry... so... bye!");
						disconnect();
						return;
					}
					retry++;
				}
			} catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		}));

	}

	protected abstract void disconnect();


	public NodeAddress getNodeAddress(String bundleId) {
		return getCurrentView().stream()
				.filter(address -> bundleId.equals(address.getBundleId()))
				.findAny().orElseThrow(() -> new NodeNotFoundException("Node %s not found".formatted(bundleId)));
	}

	private record Handlers(Consumer<Serializable> success, Consumer<ThrowableWrapper> fail) {
	}

	public void setMessageReceiver(BiConsumer<NodeAddress, Serializable> messageReceiver) {
		this.messageReceiver = messageReceiver;
	}

	public void setRequestReceiver(TriConsumer<NodeAddress, Serializable, MessageBusResponseSender> requestReceiver) {
		this.requestReceiver = requestReceiver;
	}


	/**
	 * Send a message to an address
	 *
	 * @param address the destination address
	 * @param message the message to send
	 */
	public abstract void cast(NodeAddress address, Serializable message) throws Exception;

	/**
	 * Broadcast a message
	 *
	 * @param message the message to send
	 */
	public abstract void broadcast(Serializable message) throws Exception;


	/**
	 * Send a message to multiple addresses
	 *
	 * @param addresses receiver list
	 * @param message   the message to send
	 */
	public void multicast(Collection<NodeAddress> addresses, Serializable message) throws Exception {
		for (NodeAddress address : addresses)
		{
			cast(address, message);
		}
	}

	/**
	 * Cast a message and wait for a response
	 *
	 * @param address         the destination address
	 * @param message         the message to send
	 * @param responseHandler the response handler
	 */
	public final void request(NodeAddress address, Serializable message, Consumer<Serializable> responseHandler, Consumer<ThrowableWrapper> errorHandler) throws Exception {
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

	public final CompletableFuture<Serializable> request(NodeAddress address, Serializable message) throws Exception {
		var future = new CompletableFuture<Serializable>();
		request(address, message, future::complete, error -> future.completeExceptionally(error.toThrowable()));
		return future;
	}

	public void request(NodeAddress address, Serializable message, Consumer<Serializable> responseHandler) throws Exception {
		this.request(address, message, responseHandler, error -> {
		});
	}

	public NodeAddress findNodeAddress(String bundleId) {
		return getCurrentAvailableView().stream()
				.filter(address -> bundleId.equals(address.getBundleId()))
				.findAny().orElseThrow(() -> new NodeNotFoundException("Node %s not found".formatted(bundleId)));
	}

	public abstract NodeAddress getAddress();

	public Set<NodeAddress> findAllNodeAddresses(String bundleId) {
		return getCurrentAvailableView().stream()
				.filter(address -> bundleId.equals(address.getBundleId()))
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
		System.exit(0);
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

	public void addLeaveListener(Consumer<NodeAddress> onBundleJoin) {
		this.leaveListeners.add(onBundleJoin);
	}

	public void removeLeaveListener(Consumer<NodeAddress> onBundleJoin) {
		this.leaveListeners.remove(onBundleJoin);
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

	public boolean isBundleAvailable(String bundleId) {
		return getCurrentView().stream()
				.filter(a -> a.getBundleId().equals(bundleId))
				.anyMatch(this.availableNodes::contains);
	}


	private void receive(NodeAddress src, Serializable message) {
		new Thread(() -> {
			if (message instanceof CorrelatedMessage cm)
			{
				if (cm.getIsResponse())
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
						requestReceiver.accept(src, cm.getBody(), resp);
					} catch (Exception e)
					{
						e.printStackTrace();
						resp.sendError(e);
					}
				}
			} else if (message instanceof ClusterNodeStatusUpdateMessage u)
			{
				if (u.getNewStatus())
				{
					if (!this.availableNodes.contains(src))
					{
						this.availableNodes.add(src);
						try
						{
							cast(src, new ClusterNodeStatusUpdateMessage(this.enabled));
							logger.info("ENABLED " + src.getNodeId());
						} catch (Exception e)
						{
							e.printStackTrace();
						}
						joinListeners.stream().toList().forEach(c -> c.accept(src));
						availableViewListeners.stream().toList().forEach(l -> l.accept(availableNodes));

						if (getAddress().getBundleId().equals(src.getBundleId()) && getAddress().getBundleVersion() < src.getBundleVersion())
						{
							logger.info("Newer bundle version detected (current: %d -> newer: %d), stepping aside :( ".formatted(getAddress().getBundleVersion(), src.getBundleVersion()));
							gracefulShutdown();
						}
					}

				} else
				{
					this.availableNodes.remove(src);
					leaveListeners.stream().toList().forEach(c -> c.accept(src));
					availableViewListeners.stream().toList().forEach(l -> l.accept(availableNodes));
				}
			} else if (message instanceof ClusterNodeKillMessage k)
			{
				logger.info("ClusterNodeKillMessage received from %s".formatted(src));
				gracefulShutdown();
			} else
			{
				messageReceiver.accept(src, message);
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


	public Set<NodeAddress> getCurrentView() {
		return currentView;
	}


}
