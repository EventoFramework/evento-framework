package org.evento.common.messaging.consumer;

public class ConsumerState {
    private String consumerId;
    private Long lastEventSequenceNumber;

    public ConsumerState() {
    }

    public ConsumerState(String consumerId, Long lastEventSequenceNumber) {
        this.consumerId = consumerId;
        this.lastEventSequenceNumber = lastEventSequenceNumber;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    public Long getLastEventSequenceNumber() {
        return lastEventSequenceNumber;
    }

    public void setLastEventSequenceNumber(Long lastEventSequenceNumber) {
        this.lastEventSequenceNumber = lastEventSequenceNumber;
    }
}
