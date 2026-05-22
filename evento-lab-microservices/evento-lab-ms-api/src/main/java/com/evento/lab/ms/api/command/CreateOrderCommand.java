package com.evento.lab.ms.api.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class CreateOrderCommand extends DomainCommand {

    private String orderId;
    private String description;
    private int quantity;
    private String context;

    public CreateOrderCommand() {
    }

    public CreateOrderCommand(String orderId, String description, int quantity) {
        this.orderId = orderId;
        this.description = description;
        this.quantity = quantity;
    }

    public CreateOrderCommand(String orderId, String description, int quantity, String context) {
        this.orderId = orderId;
        this.description = description;
        this.quantity = quantity;
        this.context = context;
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

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }
}
