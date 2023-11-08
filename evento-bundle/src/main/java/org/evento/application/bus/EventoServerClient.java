package org.evento.application.bus;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.evento.common.messaging.bus.EventoServer;
import org.evento.common.messaging.bus.SendFailedException;
import org.evento.common.modeling.exceptions.ExceptionWrapper;
import org.evento.common.modeling.messaging.message.internal.EventoMessage;
import org.evento.common.modeling.messaging.message.internal.EventoRequest;
import org.evento.common.modeling.messaging.message.internal.EventoResponse;
import org.evento.common.modeling.messaging.message.internal.discovery.BundleRegistration;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EventoServerClient implements EventoServer {

    private final String bundleId;
    private final long bundleVersion;
    private final String instanceId;


    private final Map<String, CompletableFuture> correlations = new HashMap<>();
    private ClusterConnection clusterConnection;

    private final RequestHandler requestHandler;

    private EventoServerClient(String bundleId,
                               long bundleVersion,
                               String instanceId,
                               RequestHandler requestHandler) {
        this.bundleId = bundleId;
        this.bundleVersion = bundleVersion;
        this.instanceId = instanceId;
        this.requestHandler = requestHandler;
    }

    public void enable() {
        clusterConnection.enable();
    }

    public void disable() {
        clusterConnection.disable();
    }

    public void close() {
        clusterConnection.close();
    }


    private void setClusterConnection(ClusterConnection cc) {
        this.clusterConnection = cc;
    }

    private void onMessage(Serializable message, EventoResponseSender responseSender) {
        if (message instanceof EventoResponse response) {
            var c = correlations.get(response.getCorrelationId());
            if (response.getBody() instanceof ExceptionWrapper tw) {
                c.completeExceptionally(tw.toException());
            } else {
                try {
                    c.complete(response.getBody());
                } catch (Exception e) {
                    c.completeExceptionally(e);
                }
            }
            correlations.remove(response.getCorrelationId());
        } else if (message instanceof EventoRequest request) {
            var resp = new EventoResponse();
            resp.setCorrelationId(request.getCorrelationId());
            try {
                var body = request.getBody();
                resp.setBody(requestHandler.handle(body));
                responseSender.send(resp);
            } catch (Exception e) {
                resp.setBody(new ExceptionWrapper(e));
                try {
                    responseSender.send(resp);
                } catch (SendFailedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        } else {
            throw new RuntimeException("Invalid message: " + message);
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

        future = future.whenComplete((t, throwable) -> {
            correlations.remove(message.getCorrelationId());
        });

        try {
            message.setTimestamp(Instant.now().toEpochMilli());
            clusterConnection.send(message);
        } catch (Exception e) {
            correlations.remove(message.getCorrelationId());
            throw e;
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
        var message = new EventoMessage();
        message.setSourceBundleId(bundleId);
        message.setSourceBundleVersion(bundleVersion);
        message.setSourceInstanceId(instanceId);
        message.setBody(m);
        clusterConnection.send(message);
    }

    public static class Builder {
        private final BundleRegistration bundleRegistration;
        private final ObjectMapper objectMapper;

        private final List<ClusterNodeAddress> addresses;
        private final RequestHandler requestHandler;

        public Builder(BundleRegistration bundleRegistration, ObjectMapper objectMapper,
                       List<ClusterNodeAddress> addresses,
                       RequestHandler requestHandler) {
            this.bundleRegistration = bundleRegistration;
            this.objectMapper = objectMapper;
            this.addresses = addresses;
            this.requestHandler = requestHandler;
            this.maxRetryAttempts = addresses.size() * 2;
        }


        private int maxRetryAttempts;
        private int retryDelayMillis = 500;


        private int maxDisableAttempts = 5;
        private int disableDelayMillis = 5000;


        private int maxReconnectAttempts = 5;
        private long reconnectDelayMillis = 2000;


        public BundleRegistration getBundleRegistration() {
            return bundleRegistration;
        }

        public ObjectMapper getObjectMapper() {
            return objectMapper;
        }

        public List<ClusterNodeAddress> getAddresses() {
            return addresses;
        }

        public RequestHandler getRequestHandler() {
            return requestHandler;
        }

        public int getMaxRetryAttempts() {
            return maxRetryAttempts;
        }

        public Builder setMaxRetryAttempts(int maxRetryAttempts) {
            this.maxRetryAttempts = maxRetryAttempts;
            return this;
        }

        public int getRetryDelayMillis() {
            return retryDelayMillis;
        }

        public Builder setRetryDelayMillis(int retryDelayMillis) {
            this.retryDelayMillis = retryDelayMillis;
            return this;
        }

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

        public int getMaxReconnectAttempts() {
            return maxReconnectAttempts;
        }

        public Builder setMaxReconnectAttempts(int maxReconnectAttempts) {
            this.maxReconnectAttempts = maxReconnectAttempts;
            return this;
        }

        public long getReconnectDelayMillis() {
            return reconnectDelayMillis;
        }

        public Builder setReconnectDelayMillis(long reconnectDelayMillis) {
            this.reconnectDelayMillis = reconnectDelayMillis;
            return this;
        }

        public EventoServerClient connect() throws InterruptedException {
            var bus = new EventoServerClient(
                    bundleRegistration.getBundleId(),
                    bundleRegistration.getBundleVersion(),
                    bundleRegistration.getInstanceId(),
                    requestHandler);
            var cc = new ClusterConnection.Builder(
                    addresses,
                    bundleRegistration,
                    (message, sender) -> {
                        bus.onMessage(message, r -> {
                                r.setTimestamp(Instant.now().toEpochMilli());
                                sender.send(r);
                        });
                    }
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
