package org.eventrails.modeling.messaging.message.bus;

import org.eventrails.modeling.exceptions.ThrowableWrapper;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface MessageBus {

	public void setMessageReceiver(Consumer<Object> messageReceiver);

	public void setRequestReceiver(BiConsumer<Object, ResponseSender> requestReceiver);


	/**
	 * Broadcast a message
	 * @param message the message to send
	 */
	public void broadcast(Object message) throws Exception;

	/**
	 * Send a message to an address
	 * @param address the destination address
	 * @param message the message to send
	 */
	public void cast(NodeAddress address, Object message) throws Exception;

	/**
	 * Send a message to multiple addresses
	 * @param addresses receiver list
	 * @param message the message to send
	 */
	public void multicast(Collection<NodeAddress> addresses, Object message) throws Exception;

	/**
	 * Cast a message and wait for a response
	 * @param address the destination address
	 * @param message the message to send
	 * @param responseHandler the response handler
	 */
	public void cast(NodeAddress address, Object message, Consumer<Object> responseHandler, Consumer<ThrowableWrapper> errorHandler) throws Exception;

	public default void cast(NodeAddress address, Object message, Consumer<Object> responseHandler) throws Exception {
		this.cast(address, message, responseHandler, error -> {});
	}

	NodeAddress findNodeAddress(String serverName);

	NodeAddress getAddress();

	List<NodeAddress> getAddresses(String serverNodeName);
}
