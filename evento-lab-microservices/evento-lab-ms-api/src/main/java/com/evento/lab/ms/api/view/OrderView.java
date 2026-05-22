package com.evento.lab.ms.api.view;

import com.evento.common.modeling.messaging.payload.View;

import java.util.ArrayList;
import java.util.List;

public class OrderView implements View {

    private String orderId;
    private String description;
    private int quantity;
    private String status;
    private boolean cancelled;
    private List<OrderItemView> items;
    private String paymentStatus;
    private String context;

    public OrderView() {
        this.items = new ArrayList<>();
    }

    public OrderView(String orderId, String description, int quantity, String status, boolean cancelled) {
        this.orderId = orderId;
        this.description = description;
        this.quantity = quantity;
        this.status = status;
        this.cancelled = cancelled;
        this.items = new ArrayList<>();
    }

    public OrderView(String orderId, String description, int quantity, String status, boolean cancelled,
                     List<OrderItemView> items, String paymentStatus, String context) {
        this.orderId = orderId;
        this.description = description;
        this.quantity = quantity;
        this.status = status;
        this.cancelled = cancelled;
        this.items = items != null ? items : new ArrayList<>();
        this.paymentStatus = paymentStatus;
        this.context = context;
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

    public List<OrderItemView> getItems() {
        return items;
    }

    public void setItems(List<OrderItemView> items) {
        this.items = items;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }
}
