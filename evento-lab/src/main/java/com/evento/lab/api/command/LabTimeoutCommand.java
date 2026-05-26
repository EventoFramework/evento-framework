package com.evento.lab.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

public class LabTimeoutCommand extends ServiceCommand {

    private long millis;
    private long times;

    public LabTimeoutCommand() {}

    public LabTimeoutCommand(long millis, long times) {
        this.millis = millis;
        this.times = times;
    }

    public long getMillis() { return millis; }
    public void setMillis(long millis) { this.millis = millis; }
    public long getTimes() { return times; }
    public void setTimes(long times) { this.times = times; }
}
