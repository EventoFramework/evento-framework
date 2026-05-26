package com.evento.lab.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

public class StressServiceCallCommand extends ServiceCommand {

    private String stressId;
    private long instance;

    public StressServiceCallCommand() {}

    public StressServiceCallCommand(String stressId, long instance) {
        this.stressId = stressId;
        this.instance = instance;
    }

    public String getStressId() { return stressId; }
    public void setStressId(String stressId) { this.stressId = stressId; }
    public long getInstance() { return instance; }
    public void setInstance(long instance) { this.instance = instance; }
}
