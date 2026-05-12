package com.evento.demo.api.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;
import com.evento.common.modeling.messaging.payload.ServiceCommand;

import java.time.ZonedDateTime;

public class ServiceStressCallCommand extends ServiceCommand {

    private String stressIdentifier;
    private long instance;
    private ZonedDateTime timestamp;

    public ServiceStressCallCommand(String stressIdentifier, long instance) {
        this.stressIdentifier = stressIdentifier;
        this.instance = instance;
        timestamp = ZonedDateTime.now();
    }

    public ServiceStressCallCommand() {}

    public String getStressIdentifier() {
        return stressIdentifier;
    }
    public void setStressIdentifier(String stressIdentifier) {
        this.stressIdentifier = stressIdentifier;
    }
    public long getInstance() {
        return instance;
    }
    public void setInstance(long instance) {}

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
