package org.evento.common.messaging.utils;

import org.evento.common.modeling.messaging.message.bus.NodeAddress;
import org.evento.common.modeling.exceptions.NodeNotFoundException;
import org.evento.common.messaging.bus.MessageBus;

import java.util.HashMap;

public class RoundRobinAddressPicker implements AddressPicker {

	private static final int RESET = 1000;

	private final HashMap<String, Integer> counters = new HashMap<>();
	private final MessageBus messageBus;

	public RoundRobinAddressPicker(MessageBus messageBus) {
		this.messageBus = messageBus;
	}

	@Override
	public synchronized NodeAddress pickNodeAddress(String serverName) {
		var addresses = messageBus.findAllNodeAddresses(serverName);
		if(addresses.isEmpty()) throw  new NodeNotFoundException("Node %s not found".formatted(serverName));
		var counter = counters.getOrDefault(serverName, 0);
		counters.put(serverName, (counter + 1) % RESET);
		return addresses.stream().sorted().toList().get(counter % addresses.size());
	}
}
