package com.evento.lab.ms.command.aggregate;

import com.evento.common.modeling.state.AggregateState;

public class OrderAggregateState extends AggregateState {

    private String description;
    private int quantity;

    public OrderAggregateState() {
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
