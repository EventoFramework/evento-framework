package com.evento.server.es.pg.outcome;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

public class OutcomePayCommand extends ServiceCommand {
    private String orderId;

    public OutcomePayCommand() {
    }

    public OutcomePayCommand(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }

    @Override
    public String getLockId() {
        return "payment-" + orderId;
    }
}
