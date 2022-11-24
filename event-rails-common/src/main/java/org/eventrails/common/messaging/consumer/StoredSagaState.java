package org.eventrails.common.messaging.consumer;

import org.eventrails.common.modeling.state.SagaState;

public class StoredSagaState {
    private Long id;
    private SagaState state;

    public StoredSagaState(Long id, SagaState state) {
        this.id = id;
        this.state = state;
    }

    public StoredSagaState() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SagaState getState() {
        return state;
    }

    public void setState(SagaState state) {
        this.state = state;
    }
}
