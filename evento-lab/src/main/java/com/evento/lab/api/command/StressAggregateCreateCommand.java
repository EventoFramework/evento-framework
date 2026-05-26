package com.evento.lab.api.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class StressAggregateCreateCommand extends DomainCommand {

    private String stressId;
    private long instances;

    public StressAggregateCreateCommand() {}

    public StressAggregateCreateCommand(String stressId, long instances) {
        this.stressId = stressId;
        this.instances = instances;
    }

    @Override
    public String getAggregateId() { return "STRS_" + stressId; }

    public String getStressId() { return stressId; }
    public void setStressId(String stressId) { this.stressId = stressId; }
    public long getInstances() { return instances; }
    public void setInstances(long instances) { this.instances = instances; }
}
