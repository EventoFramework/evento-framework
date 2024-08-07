package com.evento.application.bus;

import lombok.Getter;

import java.util.List;

/**
 * Represents the configuration for the MessageBus.
 */
@Getter
public class EventoServerMessageBusConfiguration {

    // List of cluster node addresses
    private final List<ClusterNodeAddress> addresses;

    // Maximum number of retry attempts for sending messages
    private int maxRetryAttempts;

    // Delay (in milliseconds) between retry attempts for sending messages
    private int retryDelayMillis = 500;

    // Maximum number of reconnect attempts for the cluster connection
    private int maxReconnectAttempts = -1;

    // Delay (in milliseconds) between reconnect attempts for the cluster connection
    private long reconnectDelayMillis = 5000;

    // Maximum number of attempts to disable the bus during shutdown
    private int maxDisableAttempts = 5;

    // Delay (in milliseconds) between disable attempts during shutdown
    private int disableDelayMillis = 5000;

    /**
     * Constructor for MessageBusConfiguration.
     * @param addresses The cluster node addresses.
     */
    public EventoServerMessageBusConfiguration(ClusterNodeAddress... addresses) {
        if(addresses.length < 1){
            throw new IllegalArgumentException("Addresses must contain at least one address, no address specified for event bus configuration");
        }
        this.addresses = List.of(addresses);
        // Set default maxRetryAttempts based on the number of addresses
        maxRetryAttempts = addresses.length * 2;
    }

    /**
     * Sets the maximum number of retry attempts for sending messages.
     * @param maxRetryAttempts The maximum number of retry attempts.
     * @return The MessageBusConfiguration instance.
     */
    public EventoServerMessageBusConfiguration setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
        return this;
    }

    /**
     * Sets the delay (in milliseconds) between retry attempts for sending messages.
     * @param retryDelayMillis The delay between retry attempts.
     * @return The MessageBusConfiguration instance.
     */
    public EventoServerMessageBusConfiguration setRetryDelayMillis(int retryDelayMillis) {
        this.retryDelayMillis = retryDelayMillis;
        return this;
    }

    /**
     * Sets the maximum number of reconnect attempts for the cluster connection.
     * @param maxReconnectAttempts The maximum number of reconnect attempts.
     * @return The MessageBusConfiguration instance.
     */
    public EventoServerMessageBusConfiguration setMaxReconnectAttempts(int maxReconnectAttempts) {
        this.maxReconnectAttempts = maxReconnectAttempts;
        return this;
    }

    /**
     * Sets the delay (in milliseconds) between reconnect attempts for the cluster connection.
     * @param reconnectDelayMillis The delay between reconnect attempts.
     * @return The MessageBusConfiguration instance.
     */
    public EventoServerMessageBusConfiguration setReconnectDelayMillis(long reconnectDelayMillis) {
        this.reconnectDelayMillis = reconnectDelayMillis;
        return this;
    }

    /**
     * Sets the maximum number of attempts to disable the bus during shutdown.
     * @param maxDisableAttempts The maximum number of disable attempts.
     * @return The MessageBusConfiguration instance.
     */
    public EventoServerMessageBusConfiguration setMaxDisableAttempts(int maxDisableAttempts) {
        this.maxDisableAttempts = maxDisableAttempts;
        return this;
    }

    /**
     * Sets the delay (in milliseconds) between disable attempts during shutdown.
     * @param disableDelayMillis The delay between disable attempts.
     * @return The MessageBusConfiguration instance.
     */
    public EventoServerMessageBusConfiguration setDisableDelayMillis(int disableDelayMillis) {
        this.disableDelayMillis = disableDelayMillis;
        return this;
    }
}
