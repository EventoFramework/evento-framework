package com.evento.lab.versioning;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;

@Aggregate
public class ItemAggregate {

    @AggregateCommandHandler(init = true)
    ItemAdded handle(AddItem cmd, ItemState state) {
        return new ItemAdded(cmd.getItemId(), cmd.getTitle(), cmd.getQuantity());
    }

    /** The reason tells what the aggregate was rebuilt to: its history, read in the current shape. */
    @AggregateCommandHandler
    ItemRenamedV2 handle(RenameItemV2 cmd, ItemState state) {
        return new ItemRenamedV2(cmd.getItemId(), cmd.getTitle(), "was " + state.getTitle());
    }

    @EventSourcingHandler
    ItemState on(ItemAdded e, ItemState state) {
        return new ItemState(e.getTitle());
    }

    @EventSourcingHandler
    void on(ItemRenamedV2 e, ItemState state) {
        state.setTitle(e.getTitle());
    }
}
