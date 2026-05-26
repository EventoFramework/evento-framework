package com.evento.lab.api.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class StressAggregateCallCommand extends DomainCommand {

    private String stressId;
    private long instance;

    public StressAggregateCallCommand() {}

    public StressAggregateCallCommand(String stressId, long instance) {
        this.stressId = stressId;
        this.instance = instance;
    }

    @Override
    public String getAggregateId() { return "STRS_" + stressId; }

    public String getStressId() { return stressId; }
    public void setStressId(String stressId) { this.stressId = stressId; }
    public long getInstance() { return instance; }
    public void setInstance(long instance) { this.instance = instance; }
}
