package com.evento.lab.legacyclients;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.lab.versioning.ItemView;

/** The query as callers not yet upgraded send it: "id", now "itemId" of FindItemV2. */
public class FindItem extends Query<Single<ItemView>> {
    private String id;

    public FindItem() {}

    public FindItem(String id) { this.id = id; }
}
