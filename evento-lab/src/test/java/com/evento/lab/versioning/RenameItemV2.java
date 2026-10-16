package com.evento.lab.versioning;

import com.evento.common.modeling.messaging.payload.DomainCommand;

/** Replaces RenameItem, which older callers still send. */
public class RenameItemV2 extends DomainCommand {
    private String itemId;
    private String title;

    public RenameItemV2() {}

    public RenameItemV2(String itemId, String title) {
        this.itemId = itemId;
        this.title = title;
    }

    @Override
    public String getAggregateId() { return itemId; }

    public String getItemId() { return itemId; }
    public String getTitle() { return title; }
}
