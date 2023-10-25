package org.evento.application.bus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.messaging.bus.SendFailedException;
import org.evento.common.modeling.messaging.message.internal.DisableMessage;
import org.evento.common.modeling.messaging.message.internal.EnableMessage;
import org.evento.common.modeling.messaging.message.internal.discovery.BundleRegistration;
import org.evento.common.serialization.ObjectMapperUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class EventoConnection {

    private static final Logger logger = LogManager.getLogger(EventoConnection.class);

    private final String serverAddress;
    private final int serverPort;
    private final int maxReconnectAttempts;
    private final long reconnectDelayMillis;

    private final BundleRegistration bundleRegistration;

    private final MessageHandler  handler;

    private int reconnectAttempt = 0;

    private final AtomicReference<DataOutputStream> out = new AtomicReference<>();
    private Socket socket;

    private boolean enabled;

    private static final AtomicInteger instanceCounter = new AtomicInteger(0);


    private final int conn = instanceCounter.incrementAndGet();
    private boolean isClosed = false;

    private EventoConnection(
            String serverAddress,
            int serverPort,
            int maxReconnectAttempts,
            long reconnectDelayMillis,
            BundleRegistration bundleRegistration,
            MessageHandler handler) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.reconnectDelayMillis = reconnectDelayMillis;
        this.handler = handler;
        this.bundleRegistration = bundleRegistration;
    }

    public void send(String message) throws SendFailedException {
        try {
            out.get().writeUTF(message);
        } catch (Exception e) {
            throw new SendFailedException(e);
        }
    }

    private void start() throws InterruptedException {
        var s = new Semaphore(0);
        new Thread(() -> {
            while (!isClosed && reconnectAttempt < maxReconnectAttempts) {
                logger.info("Socket Connection #{} to {}:{} attempt {}",
                        conn,
                        serverAddress,
                        serverPort,
                        reconnectAttempt + 1);
                try (Socket socket = new Socket(serverAddress, serverPort);
                     DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
                     DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream())) {
                    this.socket = socket;
                    logger.info("Connected to {}:{}", serverAddress, serverPort);
                    out.set(dataOutputStream);
                    reconnectAttempt = 0;
                    dataOutputStream.writeUTF(ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(bundleRegistration));
                    logger.info("Registration message sent");
                    s.release();
                    while (true) {
                        var data = dataInputStream.readUTF();
                        logger.info(data);
                        new Thread(() -> {
                            handler.handle(data, this::send);
                        }).start();
                    }
                } catch (IOException e) {
                    logger.error("Connection error %s:%d".formatted(reconnectAttempt, serverPort), e);
                    try {
                        Thread.sleep(reconnectDelayMillis);
                    } catch (InterruptedException e1) {
                        // Handle InterruptedException (if needed)
                    }
                    reconnectAttempt++;
                }

            }
            s.release();
            logger.error("Server unreachable after {} attempts. Dead socket.", reconnectAttempt);
        }).start();
        s.acquire();
    }

    public void enable() {
        enabled = true;
        logger.info("Enabling connection #{}", conn);
        try {
            out.get().writeUTF(ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(new EnableMessage()));
        } catch (Exception e) {

        }
    }

    public void disable() {
        enabled = false;
        logger.info("Disabling connection #{}", conn);
        try {
            out.get().writeUTF(ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(new DisableMessage()));
        } catch (Exception e) {

        }
    }

    public void close() {
        isClosed = true;
        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Builder {

        private final String serverAddress;
        private final int serverPort;


        private final BundleRegistration bundleRegistration;
        private final MessageHandler handler;


        public Builder(String serverAddress, int serverPort,
                       BundleRegistration bundleRegistration,
                       MessageHandler handler) {
            this.serverAddress = serverAddress;
            this.serverPort = serverPort;
            this.bundleRegistration = bundleRegistration;
            this.handler = handler;
        }

        private int maxReconnectAttempts = 5;
        private long reconnectDelayMillis = 2000;


        public Builder setMaxReconnectAttempts(int maxReconnectAttempts) {
            this.maxReconnectAttempts = maxReconnectAttempts;
            return this;
        }


        public Builder setReconnectDelayMillis(long reconnectDelayMillis) {
            this.reconnectDelayMillis = reconnectDelayMillis;
            return this;
        }


        public EventoConnection connect() throws InterruptedException {
            var s = new EventoConnection(serverAddress,
                    serverPort,
                    maxReconnectAttempts,
                    reconnectDelayMillis,
                    bundleRegistration,
                    handler);
            s.start();
            return s;
        }
    }
}
