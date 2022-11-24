package org.eventrails.common.performance;

import java.io.Serializable;

public class PerformanceMessage implements Serializable {

    private String bundle;
    private String  component;
    private String action;
    private long duration;


    public PerformanceMessage() {
    }

    public PerformanceMessage(String bundle, String component, String action, long duration) {
        this.bundle = bundle;
        this.component = component;
        this.action = action;
        this.duration = duration;
    }

    public String getBundle() {
        return bundle;
    }

    public void setBundle(String bundle) {
        this.bundle = bundle;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }
}
