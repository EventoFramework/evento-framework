package com.evento.demo.api.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;
import com.evento.common.modeling.messaging.payload.ServiceCommand;

import java.time.ZonedDateTime;

public class ServiceStressCreateCommand extends ServiceCommand {

    private String stressIdentifier;
    private long instances;
    private ZonedDateTime timestamp;

    public ServiceStressCreateCommand(String stressIdentifier, long instances) {
        this.stressIdentifier = stressIdentifier;
        this.instances = instances;
        timestamp = ZonedDateTime.now();
    }

    public ServiceStressCreateCommand() {}

    @Override
    public String getAggregateId() {
        return "STRS_" + stressIdentifier;
    }

    public String getStressIdentifier() {
        return stressIdentifier;
    }
    public void setStressIdentifier(String stressIdentifier) {
        this.stressIdentifier = stressIdentifier;
    }
    public long getInstances() {
        return instances;
    }
    public void setInstances(long instances) {}

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
