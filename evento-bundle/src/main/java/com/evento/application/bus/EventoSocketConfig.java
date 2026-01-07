package com.evento.application.bus;

import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class EventoSocketConfig {
    private int connectTimeout = 5000;     // in milliseconds
    private int readTimeout = 20000;        // in milliseconds
    @Getter @Setter
    private int pendingCorrelationCheck = 3000;
    private boolean keepAlive = true;
    private boolean tcpNoDelay = true;
    private boolean reuseAddress = true;
    @Getter
    private boolean closeOnSendError = true;
    @Getter
    private int timeoutLimit = 3;

    public EventoSocketConfig() {}

    // You can add builder-style methods or a constructor to override defaults if needed
    public Socket apply(String host, int port) throws IOException {
        Socket socket = new Socket();

        // Settings that must be done BEFORE connect()
        socket.setReuseAddress(reuseAddress);
        socket.connect(new InetSocketAddress(host, port), connectTimeout);

        // Settings that must be done AFTER connect()
        socket.setSoTimeout(readTimeout);
        socket.setKeepAlive(keepAlive);
        socket.setTcpNoDelay(tcpNoDelay);

        return socket;
    }

    // Optional setters for customization
    public EventoSocketConfig setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public EventoSocketConfig setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    public EventoSocketConfig setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
        return this;
    }

    public EventoSocketConfig setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
        return this;
    }

    public EventoSocketConfig setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
        return this;
    }

    public EventoSocketConfig setCloseOnSendError(boolean closeOnSendError) {
        this.closeOnSendError = closeOnSendError;
        return this;
    }

    public EventoSocketConfig setTimeoutLimit(int timeoutLimit) {
        this.timeoutLimit = timeoutLimit;
        return this;
    }



}
