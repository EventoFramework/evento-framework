package org.evento.application.bus;

import org.evento.common.messaging.bus.SendFailedException;
import org.evento.common.modeling.messaging.message.internal.discovery.BundleRegistration;
import org.evento.common.modeling.messaging.payload.Payload;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class ClusterConnection {
    private final List<ClusterNodeAddress> addressList;


    private final BundleRegistration bundleRegistration;
    private final int nodes;

    private final int  maxRetryAttempts;
    private final long retryDelayMillis;
    private List<EventoConnection> sockets = new ArrayList<>();
    private AtomicInteger nextNodeToUse = new AtomicInteger(0);

    public ClusterConnection(List<ClusterNodeAddress> addressList, BundleRegistration bundleRegistration, int maxRetryAttempts, long retryDelayMillis) {
        this.addressList = addressList;
        this.nodes = addressList.size();
        this.bundleRegistration = bundleRegistration;
        this.maxRetryAttempts = maxRetryAttempts;
        this.retryDelayMillis = retryDelayMillis;
    }

    public void send(String message) throws SendFailedException {
        var n = nextNodeToUse.getAndUpdate((o) -> (o + 1) % nodes);
        var attempt = 0;
        while (attempt < maxRetryAttempts) {
            try {
                var socket = sockets.get((n + attempt) % nodes);
                socket.send(message);
                return;
            } catch (SendFailedException e) {
                try {
                    Thread.sleep(retryDelayMillis);
                } catch (InterruptedException ignored) {}
                attempt++;
                if(attempt == maxRetryAttempts){
                    throw e;
                }
            }
        }
    }

    private void connect(int maxReconnectAttempts, long reconnectDelayMillis,BiConsumer<String, ClusterConnection> handler){
        for (ClusterNodeAddress clusterNodeAddress : addressList) {
            sockets.add(new EventoConnection.Builder(
                    clusterNodeAddress.getServerAddress(),
                    clusterNodeAddress.getServerPort(),
                    bundleRegistration,
                    (m) -> handler.accept(m, this)
            ).setMaxReconnectAttempts(maxReconnectAttempts)
                    .setReconnectDelayMillis(reconnectDelayMillis)
                    .connect());
        }
    }

    public void enable(){
        for (EventoConnection socket : sockets) {
            socket.enable();
        }
    }

    public void disable(){
        for (EventoConnection socket : sockets) {
            socket.disable();
        }
    }

    public void close() {
        for (EventoConnection socket : sockets) {
            socket.close();
        }
    }

    public static class Builder{

        private final List<ClusterNodeAddress> addresses;

        private final BundleRegistration bundleRegistration;
        private final BiConsumer<String, ClusterConnection> handler;

        public Builder(List<ClusterNodeAddress> addresses, BundleRegistration bundleRegistration, BiConsumer<String, ClusterConnection> handler) {
            this.addresses = addresses;
            this.bundleRegistration = bundleRegistration;
            this.handler = handler;
            this.maxRetryAttempts = addresses.size() * 2;
        }

        private int maxRetryAttempts;
        private int retryDelayMillis = 500;

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

        public Builder setMaxRetryAttempts(int maxRetryAttempts) {
            this.maxRetryAttempts = maxRetryAttempts;
            return this;
        }

        public Builder setRetryDelayMillis(int retryDelayMillis) {
            this.retryDelayMillis = retryDelayMillis;
            return this;
        }

        public ClusterConnection connect(){
            var c = new ClusterConnection(addresses, bundleRegistration, maxRetryAttempts, retryDelayMillis);
            c.connect(maxReconnectAttempts, reconnectDelayMillis, handler);
            return c;
        }
    }
}
