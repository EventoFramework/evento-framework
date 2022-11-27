package org.evento.bus;

import org.evento.common.modeling.messaging.message.bus.NodeAddress;

public class EventoNodeAddress extends NodeAddress {

    public EventoNodeAddress(String bundleId, long bundleVersion, Object address, String nodeId) {
        super(bundleId, bundleVersion, address, nodeId);
    }

    @Override
    public int compareTo(NodeAddress o) {
        return getNodeId().compareTo(o.getNodeId());
    }
}
