package com.evento.lab.versioning;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;

@Projection
public class ItemProjection {

    @QueryHandler
    Single<ItemView> handle(FindItemV2 query) {
        return Single.of(ItemStore.items.get(query.getItemId()));
    }
}
