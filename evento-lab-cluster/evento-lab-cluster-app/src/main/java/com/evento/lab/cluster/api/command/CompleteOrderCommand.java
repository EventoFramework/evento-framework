package com.evento.lab.cluster.api.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

/**
 * Completes an order. A domain command (not a service command) so it takes the
 * per-aggregate advisory lock and the aggregate can refuse a second completion.
 */
public class CompleteOrderCommand extends DomainCommand {

    private String orderId;
    private String region;
    private String idempotencyKey;

    public CompleteOrderCommand() {
    }

    public CompleteOrderCommand(String orderId, String region, String idempotencyKey) {
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
