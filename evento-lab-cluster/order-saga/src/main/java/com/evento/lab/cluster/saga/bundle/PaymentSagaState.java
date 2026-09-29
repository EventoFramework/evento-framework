package com.evento.lab.cluster.saga.bundle;

import com.evento.common.modeling.state.SagaState;

public class PaymentSagaState extends SagaState {

    private String orderId;
    private String region;
    private String phase;
    private String handledBy;

    public PaymentSagaState() {
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getHandledBy() { return handledBy; }
    public void setHandledBy(String handledBy) { this.handledBy = handledBy; }
}
