package org.evento.application.bus;

import java.util.List;

public class MessageBusConfiguration {

    private final List<ClusterNodeAddress> addresses;

     private int maxRetryAttempts;
    private int retryDelayMillis = 500;

    private int maxReconnectAttempts = 5;
    private long reconnectDelayMillis = 2000;

    private int maxDisableAttempts;
    private int disableDelayMillis = 5000;



    public MessageBusConfiguration(ClusterNodeAddress... addresses) {
        this.addresses = List.of(addresses);
        maxReconnectAttempts = addresses.length * 2;
    }

    public List<ClusterNodeAddress> getAddresses() {
        return addresses;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public MessageBusConfiguration setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
        return this;
    }

    public int getRetryDelayMillis() {
        return retryDelayMillis;
    }

    public MessageBusConfiguration setRetryDelayMillis(int retryDelayMillis) {
        this.retryDelayMillis = retryDelayMillis;
        return this;
    }

    public int getMaxReconnectAttempts() {
        return maxReconnectAttempts;
    }

    public MessageBusConfiguration setMaxReconnectAttempts(int maxReconnectAttempts) {
        this.maxReconnectAttempts = maxReconnectAttempts;
        return this;
    }

    public long getReconnectDelayMillis() {
        return reconnectDelayMillis;
    }

    public MessageBusConfiguration setReconnectDelayMillis(long reconnectDelayMillis) {
        this.reconnectDelayMillis = reconnectDelayMillis;
        return this;
    }

    public int getMaxDisableAttempts() {
        return maxDisableAttempts;
    }

    public MessageBusConfiguration setMaxDisableAttempts(int maxDisableAttempts) {
        this.maxDisableAttempts = maxDisableAttempts;
        return this;
    }

    public int getDisableDelayMillis() {
        return disableDelayMillis;
    }

    public MessageBusConfiguration setDisableDelayMillis(int disableDelayMillis) {
        this.disableDelayMillis = disableDelayMillis;
        return this;
    }
}
