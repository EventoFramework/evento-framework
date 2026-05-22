package com.evento.lab.ms.api.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class RemoveOrderItemCommand extends DomainCommand {

    private String orderId;
    private String itemId;

    public RemoveOrderItemCommand() {
    }

    public RemoveOrderItemCommand(String orderId, String itemId) {
        this.orderId = orderId;
        this.itemId = itemId;
    }

    @Override
    public String getAggregateId() {
        return orderId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }
}
