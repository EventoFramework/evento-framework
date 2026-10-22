package com.evento.bench.evento.aggregate;

import com.evento.common.modeling.state.AggregateState;

public class OrderState extends AggregateState {
    private int items;

    public int getItems() {
        return items;
    }

    public void setItems(int items) {
        this.items = items;
    }
}
