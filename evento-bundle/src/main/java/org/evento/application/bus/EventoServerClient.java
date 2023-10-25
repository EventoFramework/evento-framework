package org.evento.application.bus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.evento.common.messaging.bus.EventoServer;
import org.evento.common.messaging.bus.SendFailedException;
import org.evento.common.modeling.exceptions.ThrowableWrapper;
import org.evento.common.modeling.messaging.message.internal.EventoMessage;
import org.evento.common.modeling.messaging.message.internal.EventoRequest;
import org.evento.common.modeling.messaging.message.internal.EventoResponse;
import org.evento.common.modeling.messaging.message.internal.discovery.BundleRegistration;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class EventoServerClient implements EventoServer {

    private final String bundleId;
    private final long bundleVersion;
    private final String instanceId;
    private final ObjectMapper objectMapper;


    private final Map<String, CompletableFuture> correlations = new HashMap<>();
    private ClusterConnection clusterConnection;

    private final Function<Serializable, Serializable> requestHandler;

    private EventoServerClient(String bundleId,
                               long bundleVersion,
                               String instanceId,
                               ObjectMapper objectMapper,
                               Function<Serializable, Serializable> requestHandler) {
        this.bundleId = bundleId;
        this.bundleVersion = bundleVersion;
        this.instanceId = instanceId;
        this.requestHandler = requestHandler;
        this.objectMapper = objectMapper;
    }

    public void enable(){
        clusterConnection.enable();
    }

    public void disable(){
        clusterConnection.disable();
    }

    public void close(){
        clusterConnection.close();
    }



    private void setClusterConnection(ClusterConnection cc) {
        this.clusterConnection = cc;
    }

    private void onMessage(String m, ClusterConnection cc) {
        Serializable message = null;
        try {
            message = objectMapper.readValue(m, Serializable.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        if (message instanceof EventoResponse response) {
            var c = correlations.get(response.getCorrelationId());
            if (response.getBody() instanceof ThrowableWrapper tw) {
                c.completeExceptionally(tw.toThrowable());
            } else {
                try {
                    c.complete(response.getBody());
                } catch (Exception e) {
                    c.completeExceptionally(e);
                }
            }
            correlations.remove(response.getCorrelationId());
        } else if (message instanceof EventoRequest request) {
            try {
                var resp = new EventoResponse();
                resp.setCorrelationId(request.getCorrelationId());
                var body = request.getBody();

                resp.setBody(requestHandler.apply(body));

                clusterConnection.send(objectMapper.writeValueAsString(resp));
            } catch (Exception e) {
                try {
                    clusterConnection.send(objectMapper.writeValueAsString(new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace())));
                } catch (SendFailedException | JsonProcessingException ex) {
                    throw new RuntimeException(ex);
                }
            }
        } else {
            throw new RuntimeException("Invalid message: " + m);
        }
    }

    public <T extends Serializable> CompletableFuture<T> request(Serializable request) throws SendFailedException {
        var future = new CompletableFuture<T>();


        var message = new EventoRequest();
        message.setSourceBundleId(bundleId);
        message.setSourceBundleVersion(bundleVersion);
        message.setSourceInstanceId(instanceId);
        message.setCorrelationId(UUID.randomUUID().toString());
        message.setBody(request);
        correlations.put(message.getCorrelationId(), future);

        future = future.exceptionally(ex -> {
            correlations.remove(message.getCorrelationId());
            throw new RuntimeException(ex);
        });

        try {
            clusterConnection.send(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            correlations.remove(message.getCorrelationId());
            throw new SendFailedException(e);
        }

        return future;

    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public String getBundleId() {
        return bundleId;
    }

    public void send(Serializable m) throws SendFailedException {
        try {
            var message = new EventoMessage();
            message.setSourceBundleId(bundleId);
            message.setSourceBundleVersion(bundleVersion);
            message.setSourceInstanceId(instanceId);
            message.setBody(m);
            clusterConnection.send(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            throw new SendFailedException(e);
        }
    }

    public static class Builder {
        private final BundleRegistration bundleRegistration;
        private final ObjectMapper objectMapper;

        private final List<ClusterNodeAddress> addresses;
        private final Function<Serializable, Serializable> requestHandler;

        public Builder(BundleRegistration bundleRegistration, ObjectMapper objectMapper,
                       List<ClusterNodeAddress> addresses,
                       Function<Serializable,
                               Serializable> requestHandler) {
            this.bundleRegistration = bundleRegistration;
            this.objectMapper = objectMapper;
            this.addresses = addresses;
            this.requestHandler = requestHandler;
        }


        private int maxRetryAttempts;
        private int retryDelayMillis = 500;


        private int maxDisableAttempts;
        private int disableDelayMillis = 5000;



        private int maxReconnectAttempts = 5;
        private long reconnectDelayMillis = 2000;

        public int getMaxDisableAttempts() {
            return maxDisableAttempts;
        }

        public Builder setMaxDisableAttempts(int maxDisableAttempts) {
            this.maxDisableAttempts = maxDisableAttempts;
            return this;
        }

        public int getDisableDelayMillis() {
            return disableDelayMillis;
        }

        public Builder setDisableDelayMillis(int disableDelayMillis) {
            this.disableDelayMillis = disableDelayMillis;
            return this;
        }

        public ObjectMapper getObjectMapper() {
            return objectMapper;
        }

        public List<ClusterNodeAddress> getAddresses() {
            return addresses;
        }

        public int getMaxRetryAttempts() {
            return maxRetryAttempts;
        }

        public void setMaxRetryAttempts(int maxRetryAttempts) {
            this.maxRetryAttempts = maxRetryAttempts;
        }

        public int getRetryDelayMillis() {
            return retryDelayMillis;
        }

        public void setRetryDelayMillis(int retryDelayMillis) {
            this.retryDelayMillis = retryDelayMillis;
        }

        public int getMaxReconnectAttempts() {
            return maxReconnectAttempts;
        }

        public void setMaxReconnectAttempts(int maxReconnectAttempts) {
            this.maxReconnectAttempts = maxReconnectAttempts;
        }

        public long getReconnectDelayMillis() {
            return reconnectDelayMillis;
        }

        public void setReconnectDelayMillis(long reconnectDelayMillis) {
            this.reconnectDelayMillis = reconnectDelayMillis;
        }

        public EventoServerClient build() {
            var bus = new EventoServerClient(
                    bundleRegistration.getBundleId(),
                    bundleRegistration.getBundleVersion(),
                    bundleRegistration.getInstanceId(),
                    objectMapper,
                    requestHandler);
            var cc = new ClusterConnection.Builder(
                    addresses,
                    bundleRegistration,
                    bus::onMessage
            ).setMaxReconnectAttempts(maxReconnectAttempts)
                    .setReconnectDelayMillis(reconnectDelayMillis)
                    .setMaxRetryAttempts(maxRetryAttempts)
                    .setRetryDelayMillis(retryDelayMillis)
                    .connect();
            bus.setClusterConnection(cc);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    System.out.println("Graceful Shutdown - Started");
                    System.out.println("Graceful Shutdown - Disabling Bus");
                    bus.disable();
                    System.out.println("Waiting for bus disabled propagation...");
                    Thread.sleep(disableDelayMillis);
                    System.out.println("Graceful Shutdown - Bus Disabled");
                    var retry = 0;
                    while (true) {
                        var keys = bus.correlations.keySet();
                        System.out.println("Graceful Shutdown - Remaining correlations: %d".formatted(keys.size()));
                        System.out.println("Graceful Shutdown - Sleep...");
                        Thread.sleep(disableDelayMillis);
                        if (bus.correlations.isEmpty()) {
                            System.out.println("Graceful Shutdown - No more correlations, bye!");
                            bus.close();
                            return;
                        } else if (keys.containsAll(bus.correlations.keySet()) && retry > maxDisableAttempts) {
                            System.out.println("Graceful Shutdown - Pending correlation after " + disableDelayMillis * maxDisableAttempts + " sec of retry... so... bye!");
                            bus.close();
                            return;
                        }
                        retry++;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
            return bus;
        }
    }


}
