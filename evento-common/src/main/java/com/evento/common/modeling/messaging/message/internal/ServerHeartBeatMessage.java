package com.evento.common.modeling.messaging.message.internal;

import java.io.Serializable;


/**
 * Represents a heartbeat message sent by a server instance for monitoring or synchronization purposes.
 * This message contains the unique identifier of the server instance.
 * It is used to ensure the connectivity and availability of the server.
 */
public class ServerHeartBeatMessage implements Serializable {

    private String instanceId;

    private String hb;

    private long timestamp;

    public ServerHeartBeatMessage() {
    }

    public ServerHeartBeatMessage(String instanceId, String hb) {
        this.instanceId = instanceId;
        this.hb = hb;
        this.timestamp = System.currentTimeMillis();
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
