package com.evento.lab.bundle.command;

import com.evento.common.modeling.state.AggregateState;

public class LabAggregateState extends AggregateState {

    private String description;
    private int quantity;

    public LabAggregateState() {
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
