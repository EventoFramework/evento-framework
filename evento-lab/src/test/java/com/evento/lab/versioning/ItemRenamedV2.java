package com.evento.lab.versioning;

import com.evento.common.modeling.messaging.payload.DomainEvent;

/** Replaces ItemRenamed, which no longer exists: its stored events become this class. */
public class ItemRenamedV2 extends DomainEvent {
    private String itemId;
    private String title;
    private String reason;

    public ItemRenamedV2() {}

    public ItemRenamedV2(String itemId, String title, String reason) {
        this.itemId = itemId;
        this.title = title;
        this.reason = reason;
    }

    public String getItemId() { return itemId; }
    public String getTitle() { return title; }
    public String getReason() { return reason; }
}
