package com.evento.common.modeling.messaging.message.internal;

import java.io.Serializable;


public class ClientHeartBeatMessage implements Serializable {

    private String bundleId;

    private String instanceId;

    public ClientHeartBeatMessage() {
    }

    public ClientHeartBeatMessage(String bundleId, String instanceId) {
        this.bundleId = bundleId;
        this.instanceId = instanceId;
    }

    public String getBundleId() {
        return bundleId;
    }

    public void setBundleId(String bundleId) {
        this.bundleId = bundleId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
}
