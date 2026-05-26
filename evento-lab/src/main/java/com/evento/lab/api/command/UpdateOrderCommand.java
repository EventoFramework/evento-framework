package com.evento.lab.api.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class UpdateOrderCommand extends DomainCommand {

    private String orderId;
    private String description;
    private int quantity;
    private boolean failBeforeHandling;
    private boolean failAfterHandling;

    public UpdateOrderCommand() {}

    public UpdateOrderCommand(String orderId, String description, int quantity) {
        this.orderId = orderId;
        this.description = description;
        this.quantity = quantity;
    }

    @Override
    public String getAggregateId() { return orderId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public boolean isFailBeforeHandling() { return failBeforeHandling; }
    public void setFailBeforeHandling(boolean failBeforeHandling) { this.failBeforeHandling = failBeforeHandling; }
    public boolean isFailAfterHandling() { return failAfterHandling; }
    public void setFailAfterHandling(boolean failAfterHandling) { this.failAfterHandling = failAfterHandling; }
}
