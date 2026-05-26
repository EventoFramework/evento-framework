package com.evento.lab.bundle.command;

import com.evento.common.modeling.state.AggregateState;

import java.util.ArrayList;
import java.util.List;

public class LabStressAggregateState extends AggregateState {

    private List<Long> instances = new ArrayList<>();

    public List<Long> getInstances() { return instances; }
    public void setInstances(List<Long> instances) { this.instances = instances; }
}
