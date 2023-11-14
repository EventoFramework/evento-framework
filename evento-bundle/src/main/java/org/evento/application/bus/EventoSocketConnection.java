package org.evento.application.bus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.messaging.bus.SendFailedException;
import org.evento.common.modeling.exceptions.ExceptionWrapper;
import org.evento.common.modeling.messaging.message.internal.DisableMessage;
import org.evento.common.modeling.messaging.message.internal.EnableMessage;
import org.evento.common.modeling.messaging.message.internal.EventoRequest;
import org.evento.common.modeling.messaging.message.internal.EventoResponse;
import org.evento.common.modeling.messaging.message.internal.discovery.BundleRegistration;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.HashSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class EventoSocketConnection {

    private static final Logger logger = LogManager.getLogger(EventoSocketConnection.class);

    private final String serverAddress;
    private final int serverPort;
    private final int maxReconnectAttempts;
    private final long reconnectDelayMillis;

    private final BundleRegistration bundleRegistration;

    private final MessageHandler  handler;

    private int reconnectAttempt = 0;

    private final AtomicReference<ObjectOutputStream> out = new AtomicReference<>();
    private Socket socket;

    private boolean enabled;

    private static final AtomicInteger instanceCounter = new AtomicInteger(0);


    private final int conn = instanceCounter.incrementAndGet();
    private boolean isClosed = false;

    private final HashSet<String> pendingCorrelations = new HashSet<>();

    private EventoSocketConnection(
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

    public void send(Serializable message) throws SendFailedException {
        if(message instanceof EventoRequest r){
            this.pendingCorrelations.add(r.getCorrelationId());
        }
        try {
            out.get().writeObject(message);
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
                try (Socket socket = new Socket(serverAddress, serverPort)) {
                    var dataOutputStream = new ObjectOutputStream(socket.getOutputStream());
                    this.socket = socket;
                    logger.info("Connected to {}:{}", serverAddress, serverPort);
                    out.set(dataOutputStream);
                    reconnectAttempt = 0;
                    dataOutputStream.writeObject(bundleRegistration);
                    logger.info("Registration message sent");
                    if(enabled){
                        enable();
                    }
                    s.release();
                    var dataInputStream = new ObjectInputStream(socket.getInputStream());
                    while (true) {
                        var data = dataInputStream.readObject();
                        logger.info(data);
                        if(data instanceof EventoResponse r){
                            this.pendingCorrelations.remove(r.getCorrelationId());
                        }
                        new Thread(() -> {
                            handler.handle((Serializable) data, this::send);
                        }).start();
                    }
                } catch (Exception e) {
                    logger.error("Connection error %s:%d".formatted(reconnectAttempt, serverPort), e);
                    for (String pendingCorrelation : pendingCorrelations) {
                        var resp = new EventoResponse();
                        resp.setCorrelationId(pendingCorrelation);
                        resp.setBody(new ExceptionWrapper(e));
                        handler.handle(resp, this::send);
                    }
                    pendingCorrelations.clear();
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
            out.get().writeObject(new EnableMessage());
        } catch (Exception e) {

        }
    }

    public void disable() {
        enabled = false;
        logger.info("Disabling connection #{}", conn);
        try {
            out.get().writeObject(new DisableMessage());
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


        public EventoSocketConnection connect() throws InterruptedException {
            var s = new EventoSocketConnection(serverAddress,
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
