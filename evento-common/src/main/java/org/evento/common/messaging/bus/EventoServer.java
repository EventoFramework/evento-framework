package org.evento.common.messaging.bus;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;

public interface EventoServer {

    public void send(Serializable message) throws  SendFailedException;

    public <T extends Serializable> CompletableFuture<T> request(Serializable request) throws SendFailedException;

    String getInstanceId();

    String getBundleId();
}
