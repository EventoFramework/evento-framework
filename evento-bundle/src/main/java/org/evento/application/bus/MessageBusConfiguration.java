package org.evento.application.bus;

import lombok.Getter;

import java.util.List;

/**
 * Represents the configuration for the MessageBus.
 */
@Getter
public class MessageBusConfiguration {

    // List of cluster node addresses
    private final List<ClusterNodeAddress> addresses;

    // Maximum number of retry attempts for sending messages
    private int maxRetryAttempts;

    // Delay (in milliseconds) between retry attempts for sending messages
    private int retryDelayMillis = 500;

    // Maximum number of reconnect attempts for the cluster connection
    private int maxReconnectAttempts = 5;

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
    public MessageBusConfiguration(ClusterNodeAddress... addresses) {
        this.addresses = List.of(addresses);
        // Set default maxRetryAttempts based on the number of addresses
        maxRetryAttempts = addresses.length * 2;
    }

    /**
     * Sets the maximum number of retry attempts for sending messages.
     * @param maxRetryAttempts The maximum number of retry attempts.
     * @return The MessageBusConfiguration instance.
     */
    public MessageBusConfiguration setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
        return this;
    }

    /**
     * Sets the delay (in milliseconds) between retry attempts for sending messages.
     * @param retryDelayMillis The delay between retry attempts.
     * @return The MessageBusConfiguration instance.
     */
    public MessageBusConfiguration setRetryDelayMillis(int retryDelayMillis) {
        this.retryDelayMillis = retryDelayMillis;
        return this;
    }

    /**
     * Sets the maximum number of reconnect attempts for the cluster connection.
     * @param maxReconnectAttempts The maximum number of reconnect attempts.
     * @return The MessageBusConfiguration instance.
     */
    public MessageBusConfiguration setMaxReconnectAttempts(int maxReconnectAttempts) {
        this.maxReconnectAttempts = maxReconnectAttempts;
        return this;
    }

    /**
     * Sets the delay (in milliseconds) between reconnect attempts for the cluster connection.
     * @param reconnectDelayMillis The delay between reconnect attempts.
     * @return The MessageBusConfiguration instance.
     */
    public MessageBusConfiguration setReconnectDelayMillis(long reconnectDelayMillis) {
        this.reconnectDelayMillis = reconnectDelayMillis;
        return this;
    }

    /**
     * Sets the maximum number of attempts to disable the bus during shutdown.
     * @param maxDisableAttempts The maximum number of disable attempts.
     * @return The MessageBusConfiguration instance.
     */
    public MessageBusConfiguration setMaxDisableAttempts(int maxDisableAttempts) {
        this.maxDisableAttempts = maxDisableAttempts;
        return this;
    }

    /**
     * Sets the delay (in milliseconds) between disable attempts during shutdown.
     * @param disableDelayMillis The delay between disable attempts.
     * @return The MessageBusConfiguration instance.
     */
    public MessageBusConfiguration setDisableDelayMillis(int disableDelayMillis) {
        this.disableDelayMillis = disableDelayMillis;
        return this;
    }
}
