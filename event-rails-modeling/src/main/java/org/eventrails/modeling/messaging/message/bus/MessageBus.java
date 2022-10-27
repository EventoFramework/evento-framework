package org.eventrails.modeling.messaging.message.bus;

import org.eventrails.modeling.exceptions.ThrowableWrapper;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface MessageBus {

	public void setMessageReceiver(Consumer<Serializable> messageReceiver);

	public void setRequestReceiver(BiConsumer<Serializable, ResponseSender> requestReceiver);


	/**
	 * Broadcast a message
	 * @param message the message to send
	 */
	public void broadcast(Serializable message) throws Exception;

	/**
	 * Send a message to an address
	 * @param address the destination address
	 * @param message the message to send
	 */
	public void cast(NodeAddress address, Serializable message) throws Exception;

	/**
	 * Send a message to multiple addresses
	 * @param addresses receiver list
	 * @param message the message to send
	 */
	public void multicast(Collection<NodeAddress> addresses, Serializable message) throws Exception;

	/**
	 * Cast a message and wait for a response
	 * @param address the destination address
	 * @param message the message to send
	 * @param responseHandler the response handler
	 */
	public void cast(NodeAddress address, Serializable message, Consumer<Serializable> responseHandler, Consumer<ThrowableWrapper> errorHandler) throws Exception;

	public default void cast(NodeAddress address, Serializable message, Consumer<Serializable> responseHandler) throws Exception {
		this.cast(address, message, responseHandler, error -> {});
	}

	NodeAddress findNodeAddress(String serverName);

	NodeAddress getAddress();

	List<NodeAddress> findAllNodeAddresses(String serverNodeName);

	void enableBus() throws Exception;
	void disableBus() throws Exception;

	void gracefulShutdown();

	boolean isEnabled();


	boolean isRanchAvailable(String ranchName);

	void addJoinListener(Consumer<String> onRanchJoin);
	void removeJoinListener(Consumer<String> onRanchJoin);

	void addViewListener(Consumer<List<NodeAddress>> listener);

	void removeViewListener(Consumer<List<NodeAddress>> listener);

	void removeAvailableViewListener(Consumer<List<NodeAddress>> listener);

	void addAvailableViewListener(Consumer<List<NodeAddress>> listener);

	List<NodeAddress> getCurrentAvailableView();

	List<NodeAddress>  getCurrentView();

	void sendKill(String nodeId) throws Exception;
}
