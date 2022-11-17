package org.eventrails.common.modeling.messaging.message.internal.discovery;

import java.io.Serializable;
import java.util.ArrayList;

public class ClusterNodeApplicationDiscoveryResponse implements Serializable {

    private String bundleName;
    private ArrayList<RegisteredHandler> registeredHandlers;

    public ClusterNodeApplicationDiscoveryResponse(String bundleName, ArrayList<RegisteredHandler> registeredHandlers) {
        this.bundleName = bundleName;
        this.registeredHandlers = registeredHandlers;
    }

    public ClusterNodeApplicationDiscoveryResponse() {
    }

    public String getBundleName() {
        return bundleName;
    }

    public void setBundleName(String bundleName) {
        this.bundleName = bundleName;
    }

    public ArrayList<RegisteredHandler> getHandlers() {
        return registeredHandlers;
    }

    public void setHandlers(ArrayList<RegisteredHandler> registeredHandlers) {
        this.registeredHandlers = registeredHandlers;
    }
}
