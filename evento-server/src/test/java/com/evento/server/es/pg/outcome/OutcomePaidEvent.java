package com.evento.server.es.pg.outcome;

import com.evento.common.modeling.messaging.payload.ServiceEvent;

public class OutcomePaidEvent extends ServiceEvent {
    private String orderId;
    /** Fresh per execution: the same nonce in two answers means the second one was replayed. */
    private String nonce;

    public OutcomePaidEvent() {
    }

    public OutcomePaidEvent(String orderId, String nonce) {
        this.orderId = orderId;
        this.nonce = nonce;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getNonce() {
        return nonce;
    }
}
