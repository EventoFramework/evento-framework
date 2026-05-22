package com.evento.lab.ms.api.view;

import com.evento.common.modeling.messaging.payload.View;

public class OrderView implements View {

    private String orderId;
    private String description;
    private int quantity;
    private String status;
    private boolean cancelled;

    public OrderView() {
    }

    public OrderView(String orderId, String description, int quantity, String status, boolean cancelled) {
        this.orderId = orderId;
        this.description = description;
        this.quantity = quantity;
        this.status = status;
        this.cancelled = cancelled;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
