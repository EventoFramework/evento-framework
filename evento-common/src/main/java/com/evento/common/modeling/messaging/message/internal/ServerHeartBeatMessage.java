package com.evento.common.modeling.messaging.message.internal;

import java.io.Serializable;


/**
 * Represents a heartbeat message sent by a server instance for monitoring or synchronization purposes.
 * This message contains the unique identifier of the server instance.
 * It is used to ensure the connectivity and availability of the server.
 */
public class ServerHeartBeatMessage implements Serializable {

    private String instanceId;

    public ServerHeartBeatMessage() {
    }

    public ServerHeartBeatMessage(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
}
