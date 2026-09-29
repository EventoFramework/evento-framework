package com.evento.lab.cluster.api.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class AddOrderItemCommand extends DomainCommand {

    private String orderId;
    private String sku;
    private int qty;
    private String region;
    private String idempotencyKey;

    public AddOrderItemCommand() {
    }

    public AddOrderItemCommand(String orderId, String sku, int qty, String region, String idempotencyKey) {
        this.orderId = orderId;
        this.sku = sku;
        this.qty = qty;
        this.region = region;
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public String getAggregateId() {
        return orderId;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
