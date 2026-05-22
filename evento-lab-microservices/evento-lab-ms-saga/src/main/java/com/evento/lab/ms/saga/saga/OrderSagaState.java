package com.evento.lab.ms.saga.saga;

import com.evento.common.modeling.state.SagaState;

public class OrderSagaState extends SagaState {

    private String orderId;
    private String status;

    public OrderSagaState() {
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
