package com.evento.lab.cluster.api.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

/**
 * Creates an order in a region. Commands carry no framework-level context
 * (G23), so the region travels on the payload and the aggregate copies it onto
 * every event it emits.
 */
public class CreateOrderCommand extends DomainCommand {

    private String orderId;
    private String region;
    private String idempotencyKey;

    public CreateOrderCommand() {
    }

    public CreateOrderCommand(String orderId, String region, String idempotencyKey) {
        this.orderId = orderId;
        this.region = region;
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public String getAggregateId() {
        return orderId;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
