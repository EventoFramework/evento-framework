package org.eventrails.modeling.messaging.utils;

import org.eventrails.modeling.exceptions.NodeNotFoundException;
import org.eventrails.modeling.messaging.message.bus.MessageBus;
import org.eventrails.modeling.messaging.message.bus.NodeAddress;

import java.util.HashMap;

public class RoundRobinAddressPicker {

	private static final int RESET = 1000;

	private final HashMap<String, Integer> counters = new HashMap<>();
	private final MessageBus messageBus;

	public RoundRobinAddressPicker(MessageBus messageBus) {
		this.messageBus = messageBus;
	}

	public synchronized NodeAddress pickNodeAddress(String serverName) {
		var addresses = messageBus.findAllNodeAddresses(serverName);
		if(addresses.isEmpty()) throw  new NodeNotFoundException("Node %s not found".formatted(serverName));
		var counter = counters.getOrDefault(serverName, 0);
		counters.put(serverName, (counter + 1) % RESET);
		return addresses.get(counter % addresses.size());
	}
}
