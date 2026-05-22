package com.evento.lab.ms.api.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class AddOrderItemCommand extends DomainCommand {

    private String orderId;
    private String itemId;
    private String name;
    private double price;
    private int quantity;

    public AddOrderItemCommand() {
    }

    public AddOrderItemCommand(String orderId, String itemId, String name, double price, int quantity) {
        this.orderId = orderId;
        this.itemId = itemId;
        this.name = name;
        this.price = price;
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

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
