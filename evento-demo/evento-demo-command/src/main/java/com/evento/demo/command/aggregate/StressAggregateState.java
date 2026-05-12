package com.evento.demo.command.aggregate;

import com.evento.common.modeling.state.AggregateState;

import java.util.ArrayList;
import java.util.HashSet;

public class StressAggregateState extends AggregateState {
    private ArrayList<Long> instances = new ArrayList<>();


    public ArrayList<Long> getInstances() {
        return instances;
    }

    public void setInstances(ArrayList<Long> instances) {
        this.instances = instances;
    }
}
