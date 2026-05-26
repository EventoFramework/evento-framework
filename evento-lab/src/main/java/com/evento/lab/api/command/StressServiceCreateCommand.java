package com.evento.lab.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

public class StressServiceCreateCommand extends ServiceCommand {

    private String stressId;
    private long instances;

    public StressServiceCreateCommand() {}

    public StressServiceCreateCommand(String stressId, long instances) {
        this.stressId = stressId;
        this.instances = instances;
    }

    public String getStressId() { return stressId; }
    public void setStressId(String stressId) { this.stressId = stressId; }
    public long getInstances() { return instances; }
    public void setInstances(long instances) { this.instances = instances; }
}
