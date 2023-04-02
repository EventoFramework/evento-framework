package org.evento.common.modeling.messaging.message.internal.discovery;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ClusterNodeApplicationDiscoveryResponse implements Serializable {

    private String bundleId;
    private long bundleVersion;
    private ArrayList<RegisteredHandler> registeredHandlers;

    private boolean autorun;

    private int minInstances;

    private int maxInstances;

    private HashMap<String, String> schemas;

    public ClusterNodeApplicationDiscoveryResponse(
            String bundleId,
            long bundleVersion,
            boolean autorun,
            int minInstances,
            int maxInstances,
            ArrayList<RegisteredHandler> registeredHandlers,
            HashMap<String, String> schemas) {
        this.bundleId = bundleId;
        this.registeredHandlers = registeredHandlers;
        this.bundleVersion = bundleVersion;
        this.autorun = autorun;
        this.minInstances = minInstances;
        this.maxInstances = maxInstances;
        this.schemas = schemas;
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

    public boolean getAutorun() {
        return autorun;
    }

    public void setAutorun(boolean autorun) {
        this.autorun = autorun;
    }

    public int getMinInstances() {
        return minInstances;
    }

    public void setMinInstances(int minInstances) {
        this.minInstances = minInstances;
    }

    public int getMaxInstances() {
        return maxInstances;
    }

    public void setMaxInstances(int maxInstances) {
        this.maxInstances = maxInstances;
    }

    public HashMap<String, String> getSchemas() {
        return schemas;
    }

    public void setSchemas(HashMap<String, String> schemas) {
        this.schemas = schemas;
    }
}
