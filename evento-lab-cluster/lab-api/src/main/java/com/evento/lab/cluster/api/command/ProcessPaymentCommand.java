package com.evento.lab.cluster.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

/**
 * Issued by the payment saga. A service command: its lock id doubles as the
 * {@code aggregate_id} the server stores the resulting event under, hence the
 * {@code payment-} prefix that keeps it out of the order aggregate's story.
 */
public class ProcessPaymentCommand extends ServiceCommand {

    public static final String LOCK_PREFIX = "payment-";

    private String orderId;
    private String region;

    public ProcessPaymentCommand() {
    }

    public ProcessPaymentCommand(String orderId, String region) {
        this.orderId = orderId;
        this.region = region;
    }

    @Override
    public String getLockId() {
        return LOCK_PREFIX + orderId;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
}
