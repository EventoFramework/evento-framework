package com.evento.lab.versioning;

import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.versioning.Revision;

/** Revision 0 called the title "name" and had no quantity. */
@Revision(1)
public class ItemAdded extends DomainEvent {
    private String itemId;
    private String title;
    private int quantity;

    public ItemAdded() {}

    public ItemAdded(String itemId, String title, int quantity) {
        this.itemId = itemId;
        this.title = title;
        this.quantity = quantity;
    }

    public String getItemId() { return itemId; }
    public String getTitle() { return title; }
    public int getQuantity() { return quantity; }
}
