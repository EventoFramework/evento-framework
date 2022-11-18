package org.eventrails.bus;

import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;

public class EventRailsNodeAddress extends NodeAddress {

    public EventRailsNodeAddress(String nodeName, Object address, String nodeId) {
        super(nodeName, address, nodeId);
    }

    @Override
    public int compareTo(NodeAddress o) {
        return getNodeId().compareTo(o.getNodeId());
    }
}
