package org.eventrails.bus;

import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;

public class EventRailsNodeAddress extends NodeAddress {

    public EventRailsNodeAddress(String bundleId, long bundleVersion, Object address, String nodeId) {
        super(bundleId, bundleVersion, address, nodeId);
    }

    @Override
    public int compareTo(NodeAddress o) {
        return getNodeId().compareTo(o.getNodeId());
    }
}
