package com.evento.lab.versioning;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;

/** Replaces FindItem, which older callers still send. */
public class FindItemV2 extends Query<Single<ItemView>> {
    private String itemId;

    public FindItemV2() {}

    public FindItemV2(String itemId) { this.itemId = itemId; }

    public String getItemId() { return itemId; }
}
