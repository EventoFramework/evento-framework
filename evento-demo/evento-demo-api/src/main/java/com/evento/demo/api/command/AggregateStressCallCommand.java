package com.evento.demo.api.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

import java.time.ZonedDateTime;

public class AggregateStressCallCommand extends DomainCommand {

    private String stressIdentifier;
    private long instance;
    private ZonedDateTime timestamp;

    public AggregateStressCallCommand(String stressIdentifier, long instance) {
        this.stressIdentifier = stressIdentifier;
        this.instance = instance;
        timestamp = ZonedDateTime.now();
    }

    public AggregateStressCallCommand() {}

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
