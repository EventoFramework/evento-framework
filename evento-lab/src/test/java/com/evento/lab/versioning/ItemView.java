package com.evento.lab.versioning;

import com.evento.common.modeling.messaging.payload.View;

public class ItemView implements View {
    private String itemId;
    private String title;
    private int quantity;

    public ItemView() {}

    public ItemView(String itemId, String title, int quantity) {
        this.itemId = itemId;
        this.title = title;
        this.quantity = quantity;
    }

    public String getItemId() { return itemId; }
    public String getTitle() { return title; }
    public int getQuantity() { return quantity; }
}
