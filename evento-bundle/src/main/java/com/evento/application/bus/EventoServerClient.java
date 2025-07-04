package com.evento.application.bus;

import com.evento.application.EventoBundle;
import com.evento.common.modeling.messaging.message.internal.*;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleConsumerRegistrationMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.messaging.bus.SendFailedException;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleRegistration;
import com.evento.common.utils.Sleep;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.evento.common.serialization.ObjectMapperUtils;

/**
 * Represents a client for communicating with an Evento server.
 */
public class EventoServerClient implements EventoServer {

    private static final Logger logger = LogManager.getLogger(EventoServerClient.class);

    private final String bundleId;
    private final long bundleVersion;
    private final String instanceId;

    @SuppressWarnings("rawtypes")
    private final Map<String, Correlation> correlations = new ConcurrentHashMap<>();
    @Getter
    private ClusterConnection clusterConnection;

    private final RequestHandler requestHandler;

    /**
     * Private constructor for creating an EventoServerClient instance.
     *
     * @param bundleId                   The bundle ID of the client.
     * @param bundleVersion              The bundle version of the client.
     * @param instanceId                 The instance ID of the client.
     * @param requestHandler             The handler for processing incoming requests.
     */
    private EventoServerClient(String bundleId,
                               long bundleVersion,
                               String instanceId,
                               RequestHandler requestHandler) {
        this.bundleId = bundleId;
        this.bundleVersion = bundleVersion;
        this.instanceId = instanceId;
        this.requestHandler = requestHandler;

    }

    /**
     * Enables communication with the cluster nodes.
     */
    public void enable() {
        clusterConnection.enable();
    }

    /**
     * Disables communication with the cluster nodes.
     */
    public void disable() {
        clusterConnection.disable();
    }

    /**
     * Closes the connection to the cluster nodes.
     */
    public void close() {
        clusterConnection.close();
    }

    /**
     * Sets the cluster connection for the client.
     *
     * @param cc The cluster connection to set.
     */
    private void setClusterConnection(ClusterConnection cc) {
        this.clusterConnection = cc;
    }

    /**
     * Handles an incoming message and takes appropriate actions based on the message type.
     *
     * @param message        The incoming message.
     * @param responseSender The sender for responding to the message.
     */
    @SuppressWarnings("unchecked")
    private void onMessage(Serializable message, EventoResponseSender responseSender) {
        switch (message) {
            case EventoResponse response -> {
                // Handling response messages
                var c = correlations.get(response.getCorrelationId());
                if (response.getBody() instanceof ExceptionWrapper tw) {
                    c.getCallback().completeExceptionally(tw.toException());
                } else {
                    try {
                        c.getCallback().complete(response.getBody());
                    } catch (Exception e) {
                        c.getCallback().completeExceptionally(e);
                    }
                }
                correlations.remove(response.getCorrelationId());
            }
            case EventoRequest request -> {
                // Handling request messages
                var resp = new EventoResponse();
                resp.setCorrelationId(request.getCorrelationId());
                try {
                    var body = request.getBody();
                    resp.setBody(requestHandler.handle(body));
                    responseSender.send(resp);
                } catch (Throwable e) {
                    resp.setBody(new ExceptionWrapper(e));
                    try {
                        responseSender.send(resp);
                    } catch (SendFailedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
            case EventoMessage m -> {
                // Handling general messages
                if (m.getBody() instanceof ClusterNodeKillMessage) {
                    logger.info("ClusterNodeKillMessage received");
                    System.exit(0);
                }
            }
            case null, default ->
                // Invalid message type
                    throw new RuntimeException("Invalid message: " + message);
        }
    }

    /**
     * Sends a request to the cluster nodes and returns a CompletableFuture for the response.
     *
     * @param request The request to be sent.
     * @param <T>     The type of the response body.
     * @return A CompletableFuture representing the response.
     * @throws SendFailedException Thrown if the request fails to be sent.
     */
    public <T extends Serializable> CompletableFuture<T> request(Serializable request) throws SendFailedException {
        var future = new CompletableFuture<T>();

        var message = new EventoRequest();
        message.setSourceBundleId(bundleId);
        message.setSourceBundleVersion(bundleVersion);
        message.setSourceInstanceId(instanceId);
        message.setCorrelationId(UUID.randomUUID().toString());
        message.setBody(request);
        message.setTimestamp(Instant.now().toEpochMilli());
        correlations.put(message.getCorrelationId(), new Correlation<>(message, future));


        future = future.whenComplete((t, throwable) -> correlations.remove(message.getCorrelationId()));

        try {
            clusterConnection.send(message);
        } catch (Exception e) {
            correlations.remove(message.getCorrelationId());
            throw e;
        }

        return future;
    }

    /**
     * Gets the instance ID of the client.
     *
     * @return The instance ID.
     */
    @Override
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Gets the bundle ID of the client.
     *
     * @return The bundle ID.
     */
    @Override
    public String getBundleId() {
        return bundleId;
    }

    /**
     * Sends a message to the cluster nodes.
     *
     * @param m The message to be sent.
     * @throws SendFailedException Thrown if the message fails to be sent.
     */
    public void send(Serializable m) throws SendFailedException {
        var message = new EventoMessage();
        message.setSourceBundleId(bundleId);
        message.setSourceBundleVersion(bundleVersion);
        message.setSourceInstanceId(instanceId);
        message.setBody(m);
        clusterConnection.send(message);
    }

    public void registerConsumers(EventoBundle eventoBundle) throws SendFailedException {
        var cr = new BundleConsumerRegistrationMessage();
        cr.setProjectorConsumers(new HashMap<>());
        for (var c : eventoBundle.getProjectorManager().getProjectorEvenConsumers()) {
            var s = cr.getProjectorConsumers().computeIfAbsent(c.getProjectorName(), k -> new HashSet<>());
            s.add(c.getConsumerId());
        }
        cr.setSagaConsumers(new HashMap<>());
        for (var c : eventoBundle.getSagaManager().getSagaEventConsumers()) {
            var s = cr.getSagaConsumers().computeIfAbsent(c.getSagaName(), k -> new HashSet<>());
            s.add(c.getConsumerId());
        }
        cr.setObserverConsumers(new HashMap<>());
        for (var c : eventoBundle.getObserverManager().getObserverEventConsumers()) {
            var s = cr.getObserverConsumers().computeIfAbsent(c.getObserverName(), k -> new HashSet<>());
            s.add(c.getConsumerId());
        }
        send(cr);
    }

    /**
     * Builder class for constructing EventoServerClient instances.
     */
    @Getter
    public static class Builder {

        private final BundleRegistration bundleRegistration;

        private final ObjectMapper objectMapper;

        private final List<ClusterNodeAddress> addresses;

        private final RequestHandler requestHandler;

        /**
         * Constructs a Builder instance with required parameters.
         *
         * @param bundleRegistration         The registration information for the client.
         * @param objectMapper               The ObjectMapper for JSON serialization/deserialization.
         * @param addresses                  The addresses of the cluster nodes.
         * @param requestHandler             The handler for processing incoming requests.
         */
        public Builder(BundleRegistration bundleRegistration, ObjectMapper objectMapper,
                       List<ClusterNodeAddress> addresses,
                       RequestHandler requestHandler) {
            this.bundleRegistration = bundleRegistration;
            this.objectMapper = objectMapper;
            this.addresses = addresses;
            this.requestHandler = requestHandler;
            this.maxRetryAttempts = addresses.size() * 2;
        }

        // Optional parameters with default values
        private int maxRetryAttempts;

        private int retryDelayMillis = 500;

        private int maxDisableAttempts = 5;

        private int disableDelayMillis = 5000;

        private int maxReconnectAttempts = -1;

        private long reconnectDelayMillis = 2000;

        /**
         * Sets the maximum number of retry attempts for sending messages.
         *
         * @param maxRetryAttempts The maximum number of retry attempts.
         * @return The Builder instance.
         */
        public Builder setMaxRetryAttempts(int maxRetryAttempts) {
            this.maxRetryAttempts = maxRetryAttempts;
            return this;
        }

        /**
         * Sets the delay (in milliseconds) between retry attempts for sending messages.
         *
         * @param retryDelayMillis The delay between retry attempts.
         * @return The Builder instance.
         */
        public Builder setRetryDelayMillis(int retryDelayMillis) {
            this.retryDelayMillis = retryDelayMillis;
            return this;
        }

        /**
         * Sets the maximum number of attempts to disable the bus during shutdown.
         *
         * @param maxDisableAttempts The maximum number of disable attempts.
         * @return The Builder instance.
         */
        public Builder setMaxDisableAttempts(int maxDisableAttempts) {
            this.maxDisableAttempts = maxDisableAttempts;
            return this;
        }

        /**
         * Sets the delay (in milliseconds) between disable attempts during shutdown.
         *
         * @param disableDelayMillis The delay between disable attempts.
         * @return The Builder instance.
         */
        public Builder setDisableDelayMillis(int disableDelayMillis) {
            this.disableDelayMillis = disableDelayMillis;
            return this;
        }

        /**
         * Sets the maximum number of reconnect attempts for the cluster connection.
         *
         * @param maxReconnectAttempts The maximum number of reconnect attempts.
         * @return The Builder instance.
         */
        public Builder setMaxReconnectAttempts(int maxReconnectAttempts) {
            this.maxReconnectAttempts = maxReconnectAttempts;
            return this;
        }

        /**
         * Sets the delay (in milliseconds) between reconnect attempts for the cluster connection.
         *
         * @param reconnectDelayMillis The delay between reconnect attempts.
         * @return The Builder instance.
         */
        public Builder setReconnectDelayMillis(long reconnectDelayMillis) {
            this.reconnectDelayMillis = reconnectDelayMillis;
            return this;
        }

        /**
         * Connects the EventoServerClient to the cluster.
         *
         * @return The connected EventoServerClient.
         * @throws InterruptedException Thrown if the connection is interrupted.
         */
        public EventoServerClient connect() throws InterruptedException {
            var bus = new EventoServerClient(
                    bundleRegistration.getBundleId(),
                    bundleRegistration.getBundleVersion(),
                    bundleRegistration.getInstanceId(),
                    requestHandler);
            var cc = new ClusterConnection.Builder(
                    addresses,
                    bundleRegistration,
                    (message, sender) -> bus.onMessage(message, r -> {
                        r.setTimestamp(Instant.now().toEpochMilli());
                        sender.send(r);
                    })
            ).setMaxReconnectAttempts(maxReconnectAttempts)
                    .setReconnectDelayMillis(reconnectDelayMillis)
                    .setMaxRetryAttempts(maxRetryAttempts)
                    .setRetryDelayMillis(retryDelayMillis)
                    .connect();
            bus.setClusterConnection(cc);
            // Shutdown hook for graceful shutdown
            var t = new Thread(() -> {
                try {

                    System.out.println("Graceful Shutdown - Started!");
                    System.out.println( "Graceful Shutdown - Disabling Bus");
                    bus.disable();
                    System.out.println( "Graceful Shutdown - Waiting for bus disabled propagation...");
                    Sleep.apply(disableDelayMillis);
                    System.out.println( "Graceful Shutdown - Bus Disabled");
                    var retry = 0;
                    while (true) {
                        var keys = bus.correlations.keySet();
                        System.out.printf("Graceful Shutdown - Remaining correlations: %s%n", keys.size());
                        bus.correlations.forEach((k, v) -> {
                            System.out.printf( "Graceful Shutdown - Pending correlation: %s%n", k);
                            System.out.println( "Graceful Shutdown - Body:");
                            try {
                                System.out.println(ObjectMapperUtils.getPayloadObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(v.getRequest()));
                            } catch (JsonProcessingException ignored) {}

                        });
                        System.out.println( "Graceful Shutdown - Sleep...");
                        Sleep.apply(disableDelayMillis);
                        if (bus.correlations.isEmpty()) {
                            System.out.println( "Graceful Shutdown - No more correlations, bye!");
                            bus.close();
                            return;
                        } else if (keys.containsAll(bus.correlations.keySet()) && retry > maxDisableAttempts) {
                            System.out.println( "Graceful Shutdown - Pending correlation after " + disableDelayMillis * maxDisableAttempts + " sec of retry... so... bye!");
                            bus.close();
                            return;
                        }
                        retry++;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            t.setName("Shutdown thread");
            Runtime.getRuntime().addShutdownHook(t);
            return bus;
        }
    }

    /**
     * Retrieves the collection of pending correlations.
     *
     * This method returns a collection of {@link Correlation} objects currently pending in the system.
     *
     * @return a collection of {@link Correlation} objects representing the pending correlations.
     */
    @SuppressWarnings("rawtypes")
    public Collection<Correlation> getPendingCorrelations(){
        return correlations.values();
    }

}
