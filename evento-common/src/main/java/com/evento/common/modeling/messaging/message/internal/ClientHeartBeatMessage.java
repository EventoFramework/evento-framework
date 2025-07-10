package com.evento.common.modeling.messaging.message.internal;

import java.io.Serializable;


public class ClientHeartBeatMessage implements Serializable {

    private String bundleId;

    private String instanceId;

    private String hb;

    private long timestamp;

    public ClientHeartBeatMessage() {
    }

    public ClientHeartBeatMessage(String bundleId, String instanceId, String hb) {
        this.bundleId = bundleId;
        this.instanceId = instanceId;
        this.hb = hb;
        this.timestamp = System.currentTimeMillis();
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

    public String getHb() {
        return hb;
    }

    public void setHb(String hb) {
        this.hb = hb;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
