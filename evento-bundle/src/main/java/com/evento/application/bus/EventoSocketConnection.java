package com.evento.application.bus;

import com.evento.common.modeling.messaging.message.internal.*;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleRegistered;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.common.messaging.bus.SendFailedException;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleRegistration;
import com.evento.common.utils.Sleep;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
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

    private boolean connecting = true;
    private final Object connectingLock = new Object();

    // Bundle registration information
    private final BundleRegistration bundleRegistration;

    // Message handler for processing incoming messages
    private final MessageHandler handler;
    private final Consumer<EventoSocketConnection> onCloseCallback;

    // Connection state variables
    private int reconnectAttempt = 0;
    private final AtomicReference<ObjectOutputStream> out = new AtomicReference<>();
    private final AtomicReference<ObjectInputStream> in = new AtomicReference<>();
    @Getter
    private Socket socket;
    @Getter
    private boolean enabled;
    private static final AtomicInteger instanceCounter = new AtomicInteger(0);
    private final int conn = instanceCounter.incrementAndGet();
    @Getter
    private boolean closed = false;
    private final HashSet<String> pendingCorrelations = new HashSet<>();
    private final Executor threadPerRequestExecutor = Executors.newCachedThreadPool();
    @Getter
    private Thread socketReadThread;

    private final EventoSocketConfig socketConfig;

    /**
     * Constructs an instance of EventoSocketConnection with specified configuration parameters.
     *
     * @param serverAddress        the address of the server to connect to
     * @param serverPort           the port of the server to connect to
     * @param maxReconnectAttempts the maximum number of reconnect attempts when the connection is lost
     * @param reconnectDelayMillis the delay in milliseconds between reconnect attempts
     * @param bundleRegistration   the bundle registration containing details about the bundle
     * @param handler              the handler used to process incoming messages
     * @param onCloseCallback      a callback function to invoke when the connection is closed
     * @param socketConfig         the configuration options for the socket connection
     */
    private EventoSocketConnection(
            String serverAddress,
            int serverPort,
            int maxReconnectAttempts,
            long reconnectDelayMillis,
            BundleRegistration bundleRegistration,
            MessageHandler handler,
            Consumer<EventoSocketConnection> onCloseCallback,
            EventoSocketConfig socketConfig) {
        // Initialization of parameters
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.reconnectDelayMillis = reconnectDelayMillis;
        this.handler = handler;
        this.bundleRegistration = bundleRegistration;
        this.onCloseCallback = onCloseCallback;
        this.socketConfig = socketConfig;
    }

    private void write(Serializable message) throws IOException {
        try {
            synchronized (connectingLock) {
                var o = out.get();
                o.writeObject(message);
                o.flush();
            }
        } catch (Throwable e) {
            if (socketConfig.isCloseOnSendError() && socket != null) {
                socket.close();
            }
            throw e;
        }
    }

    /**
     * Sends a serializable message over the socket connection.
     *
     * @param message The message to be sent.
     * @throws SendFailedException Thrown if the message fails to be sent.
     */
    public void send(Serializable message) throws SendFailedException {
        if (closed) {
            throw new SendFailedException(new IllegalStateException("Socket connection is closed"));
        }
        if(connecting){
            throw new SendFailedException(new IllegalStateException("Socket connection is connecting"));
        }
        if (message instanceof EventoRequest r) {
            this.pendingCorrelations.add(r.getCorrelationId());
        }
        try {
            write(message);
        } catch (Throwable e) {
            if (message instanceof EventoRequest r) {
                this.pendingCorrelations.remove(r.getCorrelationId());
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
            while (!closed && (maxReconnectAttempts < 0 || reconnectAttempt < maxReconnectAttempts)) {
                // Log the current attempt to connect
                logger.info("Socket Connection #{} to {}:{} attempt {}",
                        conn,
                        serverAddress,
                        serverPort,
                        reconnectAttempt + 1);
                try {
                    synchronized (connectingLock) {
                        connecting = true;
                        if (this.socket != null) {
                            this.socket.close();
                            this.socket = null;
                            this.pendingCorrelations.clear();
                            logger.info("Previous socket Connection #{} closed", conn);
                            Sleep.apply(reconnectDelayMillis);
                        }
                        this.socket = socketConfig.apply(serverAddress, serverPort);


                        // Initialize the output stream for sending messages
                        var dataOutputStream = new ObjectOutputStream(socket.getOutputStream());
                        logger.info("Connected to {}:{}", serverAddress, serverPort);

                        // Reset the reconnect attempt count
                        reconnectAttempt = 0;

                        // Send the bundle registration information to the server
                        dataOutputStream.writeObject(bundleRegistration);
                        dataOutputStream.flush();
                        logger.info("Registration message sent");

                        // Initialize the input stream for receiving messages
                        var dataInputStream = new ObjectInputStream(socket.getInputStream());

                        var ok = (BundleRegistered) dataInputStream.readObject();
                        if (!ok.isRegistered()) {
                            throw new IllegalStateException("Bundle registration failed", ok.getException().toException());
                        } else {
                            logger.info("Bundle registered successfully in server {}", ok.getServerInstance());
                        }

                        out.set(dataOutputStream);
                        in.set(dataInputStream);

                        connecting = false;
                    }

                    // If the connection is enabled, send an enable message
                    if(enabled){
                        enable();
                    }

                    // Signal that the connection is ready
                    connectionReady.release();

                    // Continuously listen for incoming messages
                    var timeoutHits = 0;
                    while (true) {
                        try {
                            var data = in.get().readObject();
                            timeoutHits = 0;
                            if (data instanceof EventoResponse r) {
                                // Remove correlation ID from pending set on receiving a response
                                this.pendingCorrelations.remove(r.getCorrelationId());
                            }
                            // Process the incoming message in a new thread using the message handler
                            threadPerRequestExecutor.execute(() -> handler.handle((Serializable) data, this::send));
                        } catch (SocketTimeoutException ex){
                            logger.warn("Socket timeout after {} attempts", timeoutHits);
                            timeoutHits++;
                            if (timeoutHits > socketConfig.getTimeoutLimit()) {
                                logger.error("Socket timeout after {} attempts. Closing connection", socketConfig.getTimeoutLimit());
                                throw ex;
                            }

                        }
                    }
                } catch (Throwable e) {
                    // Log connection error and handle pending correlations
                    logger.error("Connection error %s:%d".formatted(reconnectAttempt, serverPort), e);
                    for (String pendingCorrelation : pendingCorrelations) {
                        var resp = new EventoResponse();
                        resp.setCorrelationId(pendingCorrelation);
                        resp.setBody(new ExceptionWrapper(e));
                        try {
                            handler.handle(resp, this::send);
                        } catch (Exception e1) {
                            logger.error(e1.getMessage(), e1);
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
            closed = true;
            onCloseCallback.accept(this);
        });
        t.setName("EventoConnection - " + serverAddress + ":" + serverPort);
        t.start();
        this.socketReadThread = t;

        // Wait for the connection to be ready (or for the maximum attempts to be reached)
        connectionReady.acquire();
    }


    /**
     * Enables the socket connection.
     */
    public void enable() {
        enabled = true;
        logger.info("Enabling connection #{}", conn);
        try {
            write(new EnableMessage());
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
            write(new DisableMessage());
        } catch (Exception e) {
            logger.error("Disabling failed", e);
        }
    }

    /**
     * Closes the socket connection.
     */
    public void close() {
        closed = true;
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

        private final EventoSocketConfig socketConfig;

        /**
         * Constructs a Builder instance with the specified parameters.
         * This is used to configure and initialize an EventoSocketConnection object.
         *
         * @param serverAddress      The address of the server to connect to.
         * @param serverPort         The port of the server to connect to.
         * @param bundleRegistration The BundleRegistration instance containing bundle-specific information.
         * @param handler            The MessageHandler responsible for processing incoming messages and sending responses.
         * @param onCloseCallback    A Consumer callback to handle actions when the EventoSocketConnection is closed.
         * @param socketConfig       The configuration settings for the EventoSocketConnection.
         */
        public Builder(String serverAddress, int serverPort,
                       BundleRegistration bundleRegistration,
                       MessageHandler handler,
                       Consumer<EventoSocketConnection> onCloseCallback,
                       EventoSocketConfig socketConfig) {
            this.serverAddress = serverAddress;
            this.serverPort = serverPort;
            this.bundleRegistration = bundleRegistration;
            this.handler = handler;
            this.onCloseCallback = onCloseCallback;
            this.socketConfig = socketConfig;
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
                    onCloseCallback,
                    socketConfig
            );
            s.start();
            return s;
        }
    }

    /**
     * Retrieves the set of pending correlation identifiers.
     * <p>
     * This method returns a collection of strings representing the identifiers
     * of messages or requests that are currently pending within the system.
     *
     * @return a HashSet containing the pending correlation identifiers
     */
    public HashSet<String> getPendingCorrelations() {
        return new HashSet<>(pendingCorrelations);
    }

}
