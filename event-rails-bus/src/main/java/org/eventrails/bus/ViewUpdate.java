package org.eventrails.bus;

import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;

import java.io.Serializable;
import java.util.Set;

public class ViewUpdate implements Serializable {
    private Set<NodeAddress> view;
    private Set<NodeAddress> newNodes;
    private Set<NodeAddress> removedNodes;

    public ViewUpdate(NodeAddress key, ViewUpdate viewUpdate) {
    }

    public ViewUpdate(Set<NodeAddress> view, Set<NodeAddress> newNodes, Set<NodeAddress> removedNodes) {
        this.view = view;
        this.newNodes = newNodes;
        this.removedNodes = removedNodes;
    }

    public Set<NodeAddress> getView() {
        return view;
    }

    public void setView(Set<NodeAddress> view) {
        this.view = view;
    }

    public Set<NodeAddress> getNewNodes() {
        return newNodes;
    }

    public void setNewNodes(Set<NodeAddress> newNodes) {
        this.newNodes = newNodes;
    }

    public Set<NodeAddress> getRemovedNodes() {
        return removedNodes;
    }

    public void setRemovedNodes(Set<NodeAddress> removedNodes) {
        this.removedNodes = removedNodes;
    }
}
