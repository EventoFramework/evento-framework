package com.evento.common.modeling.messaging.message.internal;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;


public class Correlation<T extends Serializable> {
    private EventoRequest request;
    private CompletableFuture<T> callback;

    public Correlation() {
    }

    public  Correlation(EventoRequest request, CompletableFuture<T> callback) {
        this.request = request;
        this.callback = callback;
    }

    public EventoRequest getRequest() {
        return request;
    }

    public Correlation<T> setRequest(EventoRequest request) {
        this.request = request;
        return this;
    }

    public CompletableFuture<T> getCallback() {
        return callback;
    }

    public Correlation<T> setCallback(CompletableFuture<T> callback) {
        this.callback = callback;
        return this;
    }
}
