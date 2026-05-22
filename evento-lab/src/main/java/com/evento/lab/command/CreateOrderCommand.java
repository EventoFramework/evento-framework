package com.evento.lab.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class CreateOrderCommand extends DomainCommand {

    private String orderId;
    private String description;
    private int quantity;

    public CreateOrderCommand() {
    }

    public CreateOrderCommand(String orderId, String description, int quantity) {
        this.orderId = orderId;
        this.description = description;
        this.quantity = quantity;
    }

    @Override
    public String getAggregateId() {
        return orderId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
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
