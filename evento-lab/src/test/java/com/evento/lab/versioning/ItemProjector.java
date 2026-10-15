package com.evento.lab.versioning;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;

/** Knows only the current classes: ItemAdded at revision 1 and ItemRenamedV2. */
@Projector(version = 1)
public class ItemProjector {

    @EventHandler
    void on(ItemAdded e) {
        ItemStore.items.put(e.getItemId(), new ItemView(e.getItemId(), e.getTitle(), e.getQuantity()));
    }

    @EventHandler
    void on(ItemRenamedV2 e) {
        var item = ItemStore.items.get(e.getItemId());
        ItemStore.items.put(e.getItemId(), new ItemView(e.getItemId(), e.getTitle(),
                item == null ? 0 : item.getQuantity()));
        ItemStore.renames.add(e.getItemId() + ":" + e.getReason());
    }
}
