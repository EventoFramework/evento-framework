package com.evento.application.bus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.common.messaging.bus.SendFailedException;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.message.internal.DisableMessage;
import com.evento.common.modeling.messaging.message.internal.EnableMessage;
import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleRegistration;
import com.evento.common.utils.Sleep;

import java.io.*;
import java.net.Socket;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Represents a socket connection for communication with an Evento server.
 */
public class EventoSocketConnection {

    private static final Logger logger = LogManager.getLogger(EventoSocketConnection.class);

    // Configuration parameters
    private final String serverAddress;
    private final int serverPort;
    private final int maxReconnectAttempts;
    private final long reconnectDelayMillis;

    // Bundle registration information
    private final BundleRegistration bundleRegistration;

    // Message handler for processing incoming messages
    private final MessageHandler handler;
    private final Consumer<EventoSocketConnection> onCloseCallback;

    // Connection state variables
    private int reconnectAttempt = 0;
    private final AtomicReference<ObjectOutputStream> out = new AtomicReference<>();
    private Socket socket;
    private boolean enabled;
    private static final AtomicInteger instanceCounter = new AtomicInteger(0);
    private final int conn = instanceCounter.incrementAndGet();
    private boolean isClosed = false;
    private final HashSet<String> pendingCorrelations = new HashSet<>();
    private final Executor threadPerRequestExecutor = Executors.newCachedThreadPool();

    /**
     * Creates a new EventoSocketConnection instance.
     *
     * @param serverAddress        the address of the server to connect to
     * @param serverPort           the port of the server to connect to
     * @param maxReconnectAttempts the maximum number of reconnect attempts allowed
     * @param reconnectDelayMillis the delay in milliseconds between reconnect attempts
     * @param bundleRegistration   the bundle registration containing relevant registration details
     * @param handler              the MessageHandler instance to handle incoming messages
     * @param onCloseCallback      the callback function to be executed when the connection is closed
     */
    private EventoSocketConnection(
            String serverAddress,
            int serverPort,
            int maxReconnectAttempts,
            long reconnectDelayMillis,
            BundleRegistration bundleRegistration,
            MessageHandler handler,
            Consumer<EventoSocketConnection> onCloseCallback) {
        // Initialization of parameters
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.reconnectDelayMillis = reconnectDelayMillis;
        this.handler = handler;
        this.bundleRegistration = bundleRegistration;
        this.onCloseCallback = onCloseCallback;
    }

    /**
     * Sends a serializable message over the socket connection.
     *
     * @param message The message to be sent.
     * @throws SendFailedException Thrown if the message fails to be sent.
     */
    public synchronized void send(Serializable message) throws SendFailedException {
        if(isClosed()){
            throw new SendFailedException(new IllegalStateException("Socket connection is closed"));
        }
        if (message instanceof EventoRequest r) {
            this.pendingCorrelations.add(r.getCorrelationId());
        }
        try {
            var o = out.get();
            synchronized (o) {
                o.writeObject(message);
            }
        } catch (Throwable e) {
            if (message instanceof EventoRequest r) {
                this.pendingCorrelations.add(r.getCorrelationId());
            }
            throw new SendFailedException(e);
        }
    }

    /**
     * Starts the socket connection and listens for incoming messages.
     *
     * @throws InterruptedException Thrown if the thread is interrupted during execution.
     */
    private void start() throws InterruptedException {
        // Semaphore for signaling when the connection is ready
        var connectionReady = new Semaphore(0);

        var t = new Thread(() -> {
            // Loop until the connection is closed or the maximum reconnect attempts are reached
            while (!isClosed && (maxReconnectAttempts < 0 || reconnectAttempt < maxReconnectAttempts)) {
                // Log the current attempt to connect
                logger.info("Socket Connection #{} to {}:{} attempt {}",
                        conn,
                        serverAddress,
                        serverPort,
                        reconnectAttempt + 1);

                try (Socket socket = new Socket(serverAddress, serverPort)) {
                    // Initialize the output stream for sending messages
                    var dataOutputStream = new ObjectOutputStream(socket.getOutputStream());
                    this.socket = socket;
                    logger.info("Connected to {}:{}", serverAddress, serverPort);

                    // Reset the reconnect attempt count
                    reconnectAttempt = 0;

                    // Send the bundle registration information to the server
                    dataOutputStream.writeObject(bundleRegistration);
                    logger.info("Registration message sent");

                    // Initialize the input stream for receiving messages
                    var dataInputStream = new ObjectInputStream(socket.getInputStream());

                    var ok = dataInputStream.readObject();
                    if (!((boolean) ok)) {
                        throw new IllegalStateException("Bundle registration failed");
                    }

                    out.set(dataOutputStream);

                    // If the connection is enabled, send an enable message
                    if (enabled) {
                        enable();
                    }

                    // Signal that the connection is ready
                    connectionReady.release();

                    // Continuously listen for incoming messages
                    while (true) {
                        try {
                            var data = dataInputStream.readObject();
                            if (data instanceof EventoResponse r) {
                                // Remove correlation ID from pending set on receiving a response
                                this.pendingCorrelations.remove(r.getCorrelationId());
                            }
                            // Process the incoming message in a new thread using the message handler
                            threadPerRequestExecutor.execute(() -> handler.handle((Serializable) data, this::send));
                        } catch (OptionalDataException ignored) {
                        }
                    }
                } catch (Exception e) {
                    // Log connection error and handle pending correlations
                    logger.error("Connection error %s:%d".formatted(reconnectAttempt, serverPort), e);
                    for (String pendingCorrelation : pendingCorrelations) {
                        var resp = new EventoResponse();
                        resp.setCorrelationId(pendingCorrelation);
                        resp.setBody(new ExceptionWrapper(e));
                        try {
                            handler.handle(resp, this::send);
                        } catch (Exception ignored) {
                        }
                    }
                    pendingCorrelations.clear();

                    // Sleep before attempting to reconnect
                    Sleep.apply(reconnectDelayMillis);

                    // Increment the reconnect attempt count
                    reconnectAttempt++;
                }
            }

            // Signal that the connection is ready (even if it failed to connect)
            connectionReady.release();

            // Log an error if the server is unreachable after maximum attempts
            logger.error("Server unreachable after {} attempts. Dead socket.", reconnectAttempt);
            isClosed = true;
            onCloseCallback.accept(this);
        });
        t.setName("EventoConnection - " + serverAddress + ":" + serverPort);
        t.start();

        // Wait for the connection to be ready (or for the maximum attempts to be reached)
        connectionReady.acquire();
    }

    /**
     * Checks if the socket connection is closed.
     *
     * @return {@code true} if the socket connection is closed, {@code false} otherwise
     */
    public boolean isClosed() {
        return isClosed;
    }

    /**
     * Checks if the socket connection is enabled.
     *
     * @return {@code true} if the socket connection is enabled, {@code false} otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables the socket connection.
     */
    public void enable() {
        enabled = true;
        logger.info("Enabling connection #{}", conn);
        try {
            var o = out.get();
            synchronized (o) {
                o.writeObject(new EnableMessage());
            }
        } catch (Exception e) {
            enabled = false;
            logger.error("Enabling failed", e);
        }
    }

    /**
     * Disables the socket connection.
     */
    public void disable() {
        enabled = false;
        logger.info("Disabling connection #{}", conn);
        try {
            var o = out.get();
            synchronized (o) {
                o.writeObject(new DisableMessage());
            }
        } catch (Exception e) {
            logger.error("Disabling failed", e);
        }
    }

    /**
     * Closes the socket connection.
     */
    public void close() {
        isClosed = true;
        try {
            socket.close();
        } catch (IOException e) {
            if (socket != null && !socket.isClosed()) {
                throw new RuntimeException(e);
            }

        }
    }

    /**
     * Builder class for constructing EventoSocketConnection instances.
     */
    public static class Builder {

        private final String serverAddress;
        private final int serverPort;
        private final BundleRegistration bundleRegistration;
        private final MessageHandler handler;
        private final Consumer<EventoSocketConnection> onCloseCallback;

        // Optional parameters with default values
        private int maxReconnectAttempts = -1;
        private long reconnectDelayMillis = 2000;

        /**
         * Constructs a Builder instance with required parameters.
         *
         * @param serverAddress      The address of the Evento server.
         * @param serverPort         The port on which the server is listening.
         * @param bundleRegistration The registration information for the connection.
         * @param handler            The message handler for processing incoming messages.
         */
        public Builder(String serverAddress, int serverPort,
                       BundleRegistration bundleRegistration,
                       MessageHandler handler,
                       Consumer<EventoSocketConnection> onCloseCallback) {
            this.serverAddress = serverAddress;
            this.serverPort = serverPort;
            this.bundleRegistration = bundleRegistration;
            this.handler = handler;
            this.onCloseCallback = onCloseCallback;
        }

        /**
         * Sets the maximum number of reconnect attempts.
         *
         * @param maxReconnectAttempts The maximum number of reconnect attempts.
         * @return The Builder instance for method chaining.
         */
        public Builder setMaxReconnectAttempts(int maxReconnectAttempts) {
            this.maxReconnectAttempts = maxReconnectAttempts;
            return this;
        }

        /**
         * Sets the delay between reconnection attempts.
         *
         * @param reconnectDelayMillis The delay between reconnection attempts in milliseconds.
         * @return The Builder instance for method chaining.
         */
        public Builder setReconnectDelayMillis(long reconnectDelayMillis) {
            this.reconnectDelayMillis = reconnectDelayMillis;
            return this;
        }

        /**
         * Connects and initializes an EventoSocketConnection instance.
         *
         * @return The constructed EventoSocketConnection instance.
         * @throws InterruptedException Thrown if the thread is interrupted during execution.
         */
        public EventoSocketConnection connect() throws InterruptedException {
            var s = new EventoSocketConnection(serverAddress,
                    serverPort,
                    maxReconnectAttempts,
                    reconnectDelayMillis,
                    bundleRegistration,
                    handler,
                    onCloseCallback);
            s.start();
            return s;
        }
    }
}
