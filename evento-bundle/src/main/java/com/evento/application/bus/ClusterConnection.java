package com.evento.application.bus;

import com.evento.common.messaging.bus.SendFailedException;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleRegistration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a cluster connection that manages connections to multiple nodes in a cluster.
 */
public class ClusterConnection {
    private final List<ClusterNodeAddress> addressList;

    private final BundleRegistration bundleRegistration;
    private final int nodes;

    private final int maxRetryAttempts;
    private final long retryDelayMillis;
    private final List<EventoSocketConnection> sockets = new ArrayList<>();
    private final AtomicInteger nextNodeToUse = new AtomicInteger(0);

    /**
     * Private constructor to create a ClusterConnection instance.
     * @param addressList The list of cluster node addresses.
     * @param bundleRegistration The registration information for the connection.
     * @param maxRetryAttempts The maximum number of retry attempts for sending messages.
     * @param retryDelayMillis The delay between retry attempts in milliseconds.
     */
    private ClusterConnection(List<ClusterNodeAddress> addressList, BundleRegistration bundleRegistration, int maxRetryAttempts, long retryDelayMillis) {
        this.addressList = addressList;
        this.nodes = addressList.size();
        this.bundleRegistration = bundleRegistration;
        this.maxRetryAttempts = maxRetryAttempts;
        this.retryDelayMillis = retryDelayMillis;
    }

    /**
     * Sends a serializable message to one of the cluster nodes.
     * @param message The message to be sent.
     * @throws SendFailedException Thrown if the message fails to be sent after maximum retry attempts.
     */
    public void send(Serializable message) throws SendFailedException {
        // Get the index of the next node to use in a round-robin fashion
        var n = nextNodeToUse.getAndUpdate((o) -> (o + 1) % nodes);
        var attempt = 0;

        // Attempt to send the message to a node, with retry logic
        while (attempt < (1 + maxRetryAttempts)) {

            EventoSocketConnection socket = null;
            try {
                // Get the socket for the current node and attempt
                socket = sockets.get((n + attempt) % nodes);
                // Increment the attempt counter
                attempt++;
                // Send the message using the socket
                socket.send(message);

                // Successful send, exit the loop
                return;
            } catch (SendFailedException e) {
                // Sleep before the next retry
                try {
                    Thread.sleep(retryDelayMillis);
                } catch (InterruptedException ignored) {
                    // Handle InterruptedException (if needed)
                }

                if(socket != null && socket.isClosed()){
                    sockets.remove(socket);
                }

                // If this is the last attempt, throw the SendFailedException
                if (attempt == maxRetryAttempts) {
                    throw e;
                }
            }
        }
    }

    /**
     * Establishes connections to all cluster nodes.
     * @param maxReconnectAttempts The maximum number of attempts to reconnect in case of connection failure.
     * @param reconnectDelayMillis The delay between reconnection attempts in milliseconds.
     * @param handler The message handler for processing incoming messages.
     * @throws InterruptedException Thrown if the thread is interrupted during execution.
     */
    private void connect(int maxReconnectAttempts, long reconnectDelayMillis, MessageHandler handler) throws InterruptedException {
        for (ClusterNodeAddress clusterNodeAddress : addressList) {
            sockets.add(new EventoSocketConnection.Builder(
                    clusterNodeAddress.serverAddress(),
                    clusterNodeAddress.serverPort(),
                    bundleRegistration,
                    handler
            ).setMaxReconnectAttempts(maxReconnectAttempts)
                    .setReconnectDelayMillis(reconnectDelayMillis)
                    .connect());
        }
    }

    /**
     * Enables communication with all cluster nodes.
     */
    public void enable() {
        for (EventoSocketConnection socket : sockets) {
            socket.enable();
        }
    }

    /**
     * Disables communication with all cluster nodes.
     */
    public void disable() {
        for (EventoSocketConnection socket : sockets) {
            socket.disable();
        }
    }

    /**
     * Closes connections to all cluster nodes.
     */
    public void close() {
        for (EventoSocketConnection socket : sockets) {
            socket.close();
        }
    }

    /**
     * Builder class for constructing ClusterConnection instances.
     */
    public static class Builder {

        private final List<ClusterNodeAddress> addresses;
        private final BundleRegistration bundleRegistration;
        private final MessageHandler handler;

        /**
         * Constructs a Builder instance with required parameters.
         * @param addresses The list of cluster node addresses.
         * @param bundleRegistration The registration information for the connection.
         * @param handler The message handler for processing incoming messages.
         */
        public Builder(List<ClusterNodeAddress> addresses, BundleRegistration bundleRegistration, MessageHandler handler) {
            this.addresses = addresses;
            this.bundleRegistration = bundleRegistration;
            this.handler = handler;
            this.maxRetryAttempts = addresses.size() * 2;
        }

        // Optional parameters with default values
        private int maxRetryAttempts;
        private int retryDelayMillis = 500;
        private int maxReconnectAttempts = -1;
        private long reconnectDelayMillis = 2000;

        /**
         * Sets the maximum number of reconnect attempts for each cluster node.
         * @param maxReconnectAttempts The maximum number of reconnect attempts.
         * @return The Builder instance for method chaining.
         */
        public Builder setMaxReconnectAttempts(int maxReconnectAttempts) {
            this.maxReconnectAttempts = maxReconnectAttempts;
            return this;
        }

        /**
         * Sets the delay between reconnection attempts in milliseconds.
         * @param reconnectDelayMillis The delay between reconnection attempts.
         * @return The Builder instance for method chaining.
         */
        public Builder setReconnectDelayMillis(long reconnectDelayMillis) {
            this.reconnectDelayMillis = reconnectDelayMillis;
            return this;
        }

        /**
         * Sets the maximum number of retry attempts for sending messages.
         * @param maxRetryAttempts The maximum number of retry attempts.
         * @return The Builder instance for method chaining.
         */
        public Builder setMaxRetryAttempts(int maxRetryAttempts) {
            this.maxRetryAttempts = maxRetryAttempts;
            return this;
        }

        /**
         * Sets the delay between retry attempts in milliseconds.
         * @param retryDelayMillis The delay between retry attempts.
         * @return The Builder instance for method chaining.
         */
        public Builder setRetryDelayMillis(int retryDelayMillis) {
            this.retryDelayMillis = retryDelayMillis;
            return this;
        }

        /**
         * Connects and initializes a ClusterConnection instance.
         * @return The constructed ClusterConnection instance.
         * @throws InterruptedException Thrown if the thread is interrupted during execution.
         */
        public ClusterConnection connect() throws InterruptedException {
            if (maxRetryAttempts < 0) {
                throw new IllegalArgumentException("Invalid number of retries");
            }
            var c = new ClusterConnection(addresses, bundleRegistration, maxRetryAttempts, retryDelayMillis);
            c.connect(maxReconnectAttempts, reconnectDelayMillis, handler);
            return c;
        }
    }
}
