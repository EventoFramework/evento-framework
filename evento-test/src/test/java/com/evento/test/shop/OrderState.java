package com.evento.test.shop;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.state.AggregateState;
import java.util.ArrayList;
import java.util.List;

public class OrderState extends AggregateState {
    int budget;
    final List<String> items = new ArrayList<>();
    int spent;
    public List<String> items() { return items; }
    public int spent() { return spent; }
}
