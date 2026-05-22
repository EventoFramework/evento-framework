package com.evento.lab.bundle.consumer;

import com.evento.common.modeling.state.SagaState;

public class LabSagaState extends SagaState {

    private String orderId;
    private String status;

    public LabSagaState() {
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
