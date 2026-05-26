package com.evento.lab.api.view;

import com.evento.common.modeling.messaging.payload.View;

public class OrderRichView implements View {

    private String orderId;
    private String description;
    private int quantity;
    private String status;
    private boolean cancelled;
    private long createdAt;
    private long updatedAt;

    public OrderRichView() {}

    public OrderRichView(String orderId, String description, int quantity,
                         String status, boolean cancelled, long createdAt, long updatedAt) {
        this.orderId = orderId;
        this.description = description;
        this.quantity = quantity;
        this.status = status;
        this.cancelled = cancelled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "OrderRichView{orderId='" + orderId + "', description='" + description +
               "', quantity=" + quantity + ", status='" + status + "', cancelled=" + cancelled +
               ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + '}';
    }
}
