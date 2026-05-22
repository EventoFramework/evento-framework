package com.evento.lab.ms.command.aggregate;

import com.evento.common.modeling.state.AggregateState;

import java.util.ArrayList;
import java.util.List;

public class OrderAggregateState extends AggregateState {

    private String description;
    private int quantity;
    private List<OrderItem> items;
    private String context;
    private String status;

    public OrderAggregateState() {
        this.items = new ArrayList<>();
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

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
