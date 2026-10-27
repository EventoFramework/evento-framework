package com.evento.test.shop;

import com.evento.common.modeling.annotations.component.Saga;
import com.evento.common.modeling.state.SagaState;

public class PaymentState extends SagaState {
    int total;
    public int total() { return total; }
}
