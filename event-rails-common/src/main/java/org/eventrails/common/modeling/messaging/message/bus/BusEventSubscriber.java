package org.eventrails.common.modeling.messaging.message.bus;

import java.io.Serializable;
import java.util.Set;

public interface BusEventSubscriber {

	public void onMessage(NodeAddress address, Serializable message);

	public void onViewUpdate(Set<NodeAddress> view, Set<NodeAddress> nodeAdded, Set<NodeAddress> nodeRemoved);
}
