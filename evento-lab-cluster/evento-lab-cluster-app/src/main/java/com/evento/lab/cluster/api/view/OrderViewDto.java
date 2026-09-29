package com.evento.lab.cluster.api.view;

import com.evento.common.modeling.messaging.payload.View;

/** One row of an {@code order_view_*} table, as served over HTTP and the query bus. */
public class OrderViewDto implements View {

    private String orderId;
    private String context;
    private String status;
    private int itemCount;
    private int itemQty;
    private String paymentStatus;
    private Long createdSeq;
    private long lastSeq;
    private int appliedEvents;
    private int staleSkips;
    private String updatedBy;

    public OrderViewDto() {
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }
    public int getItemQty() { return itemQty; }
    public void setItemQty(int itemQty) { this.itemQty = itemQty; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public Long getCreatedSeq() { return createdSeq; }
    public void setCreatedSeq(Long createdSeq) { this.createdSeq = createdSeq; }
    public long getLastSeq() { return lastSeq; }
    public void setLastSeq(long lastSeq) { this.lastSeq = lastSeq; }
    public int getAppliedEvents() { return appliedEvents; }
    public void setAppliedEvents(int appliedEvents) { this.appliedEvents = appliedEvents; }
    public int getStaleSkips() { return staleSkips; }
    public void setStaleSkips(int staleSkips) { this.staleSkips = staleSkips; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
