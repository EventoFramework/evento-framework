package com.evento.lab.versioning;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class AddItem extends DomainCommand {
    private String itemId;
    private String title;
    private int quantity;

    public AddItem() {}

    public AddItem(String itemId, String title, int quantity) {
        this.itemId = itemId;
        this.title = title;
        this.quantity = quantity;
    }

    @Override
    public String getAggregateId() { return itemId; }

    public String getItemId() { return itemId; }
    public String getTitle() { return title; }
    public int getQuantity() { return quantity; }
}
