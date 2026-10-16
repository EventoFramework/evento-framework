package com.evento.lab.legacyclients;

import com.evento.common.modeling.messaging.payload.DomainCommand;

/** The command as callers not yet upgraded send it: "newTitle", now "title" of RenameItemV2. */
public class RenameItem extends DomainCommand {
    private String itemId;
    private String newTitle;

    public RenameItem() {}

    public RenameItem(String itemId, String newTitle) {
        this.itemId = itemId;
        this.newTitle = newTitle;
    }

    @Override
    public String getAggregateId() { return itemId; }
}
