package org.eventrails.common.modeling.messaging.message.internal.discovery;

import java.io.Serializable;
import java.util.ArrayList;

public class ClusterNodeApplicationDiscoveryResponse implements Serializable {

    private String bundleId;
    private long bundleVersion;
    private ArrayList<RegisteredHandler> registeredHandlers;

    public ClusterNodeApplicationDiscoveryResponse(String bundleId, long bundleVersion, ArrayList<RegisteredHandler> registeredHandlers) {
        this.bundleId = bundleId;
        this.registeredHandlers = registeredHandlers;
        this.bundleVersion = bundleVersion;
    }

    public ClusterNodeApplicationDiscoveryResponse() {
    }

    public String getBundleId() {
        return bundleId;
    }

    public void setBundleId(String bundleId) {
        this.bundleId = bundleId;
    }

    public ArrayList<RegisteredHandler> getHandlers() {
        return registeredHandlers;
    }

    public void setHandlers(ArrayList<RegisteredHandler> registeredHandlers) {
        this.registeredHandlers = registeredHandlers;
    }

    public long getBundleVersion() {
        return bundleVersion;
    }

    public void setBundleVersion(long bundleVersion) {
        this.bundleVersion = bundleVersion;
    }
}
