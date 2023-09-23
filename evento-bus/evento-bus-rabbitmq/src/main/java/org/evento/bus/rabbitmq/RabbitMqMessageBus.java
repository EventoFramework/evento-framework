package org.evento.bus.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.messaging.bus.MessageNotReceivedException;
import org.evento.common.modeling.messaging.message.bus.NodeAddress;
import org.evento.common.serialization.ObjectMapperUtils;

import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class RabbitMqMessageBus extends MessageBus {

    private static final Logger logger = LogManager.getLogger(RabbitMqMessageBus.class);
    private static final String CLUSTER_BROADCAST_EXCHANGE = "cluster-broadcast";
    private static final String CLUSTER_BROADCAST_QUEUE_PREFIX = "broadcast-queue-for-";
    private static final String SIGNAL_HELLO = "hello";
    private static final String SIGNAL_JOIN = "join";
    private static final String SIGNAL_LEAVE = "leave";
    private final Channel channel;
    private final RabbitMqNodeAddress address;
    private final String exchange;
    private final Runnable close;
    private final int requestTimeout;
    private final ObjectMapper objectMapper;

    private RabbitMqMessageBus(
            RabbitMqNodeAddress nodeAddress,
            Channel channel,
            String exchange,
            int requestTimeout,
            ObjectMapper objectMapper,
            Runnable close) {
        super(subscriber -> {
            try {
                var view = new HashSet<NodeAddress>();
                view.add(nodeAddress);
                channel.basicConsume(nodeAddress.getNodeId(), true, (consumerTag, delivery) -> {
                    channel.basicPublish("", delivery.getProperties().getReplyTo(),
                            new AMQP.BasicProperties
                                    .Builder()
                                    .correlationId(delivery.getProperties().getCorrelationId())
                                    .build(), null);
                    try {
                        var rabbitMessage = RabbitMqMessage.parse(delivery.getBody(), objectMapper);
                        subscriber.onMessage(rabbitMessage.getSource(), rabbitMessage.getMessage());
                    } catch (Exception e) {
                        logger.error("Error on message received", e);
                    }

                }, consumerTag -> {
                });

                channel.basicConsume(CLUSTER_BROADCAST_QUEUE_PREFIX + nodeAddress.getNodeId(), true, (consumerTag, delivery) -> {
                    try {
                        var rabbitMessage = RabbitMqMessage.parse(delivery.getBody(), objectMapper);
                        if (rabbitMessage.getMessage() instanceof String) {
                            String signal = rabbitMessage.getMessage().toString();
                            logger.info("{} from {}", signal.toUpperCase(), rabbitMessage.getSourceNodeId());
                            if (signal.equals(SIGNAL_JOIN)) {
                                view.add(rabbitMessage.getSource());
                                subscriber.onViewUpdate(view, Set.of(rabbitMessage.getSource()), Set.of());
                                channel.basicPublish(CLUSTER_BROADCAST_EXCHANGE, "", null,
                                        RabbitMqMessage.create(nodeAddress, SIGNAL_HELLO, objectMapper));
                            }
                            if (signal.equals(SIGNAL_HELLO)) {
                                if (!view.contains(rabbitMessage.getSource())) {
                                    view.add(rabbitMessage.getSource());
                                    subscriber.onViewUpdate(view, Set.of(rabbitMessage.getSource()), Set.of());
                                }
                            } else if (signal.equals(SIGNAL_LEAVE)) {
                                view.remove(rabbitMessage.getSource());
                                subscriber.onViewUpdate(view, Set.of(), Set.of(rabbitMessage.getSource()));
                            }
                        } else {
                            subscriber.onMessage(rabbitMessage.getSource(), rabbitMessage.getMessage());
                        }
                    } catch (Exception e) {
                        logger.error("Error on Broadcast event received", e);
                    }
                }, consumerTag -> {
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        });
        this.close = close;
        this.channel = channel;
        this.address = nodeAddress;
        this.exchange = exchange;
        this.requestTimeout = requestTimeout;
        this.objectMapper = objectMapper;
    }

    @Override
    public void cast(NodeAddress address, Serializable message) throws Exception {
        final String corrId = UUID.randomUUID().toString();
        String replyQueueName = channel.queueDeclare().getQueue();
        final CompletableFuture<Void> response = new CompletableFuture<>();
        AtomicReference<String> ctag = new AtomicReference<>();
        try {
            ctag.set(channel.basicConsume(replyQueueName, true, (consumerTag, delivery) -> {
                if (corrId.equals(delivery.getProperties().getCorrelationId())) {
                    response.complete(null);
                }
            }, consumerTag -> {
            }));
        } catch (IOException e) {
            response.completeExceptionally(e);
        }
        channel.basicPublish(exchange, address.getNodeId(), new AMQP.BasicProperties.Builder()
                        .replyTo(replyQueueName)
                        .correlationId(corrId)
                        .build(),
                RabbitMqMessage.create(this.address, message, objectMapper));
        try {
            response.get(requestTimeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            channel.basicPublish(CLUSTER_BROADCAST_EXCHANGE, "", null, RabbitMqMessage.create(
                    (RabbitMqNodeAddress) address, SIGNAL_LEAVE, objectMapper));
            throw new MessageNotReceivedException(address, message);
        } finally {
            channel.basicCancel(ctag.get());
        }
    }

    @Override
    public void broadcast(Serializable message) throws Exception {
        channel.basicPublish(CLUSTER_BROADCAST_EXCHANGE, "", null,
                RabbitMqMessage.create(this.address, message, objectMapper));
    }

    @Override
    public RabbitMqNodeAddress getAddress() {
        return address;
    }


    public void disconnect() {
        close.run();
    }



    public static class Builder {
        private String bundleId;
        private Long bundleVersion;
        private String exchange;
        private ConnectionFactory factory;
        private int requestTimeout = 1000;

        private int disableWaitingTime = 15000;
        private int disableMaxRetry = 15;

        public static Builder builder() {
            return new Builder();
        }

        public RabbitMqMessageBus connect() throws Exception {

            if (bundleId == null) {
                throw new IllegalArgumentException("Missing bundle identifier");
            }
            if (bundleVersion == null) {
                throw new IllegalArgumentException("Missing bundle version");
            }
            if (exchange == null) {
                throw new IllegalArgumentException("Missing exchange");
            }
            if (factory == null) {
                throw new IllegalArgumentException("Missing connection factory");
            }
            if (requestTimeout < 1) {
                throw new IllegalArgumentException("Request timeout < 1");
            }
            if (disableWaitingTime < 1) {
                throw new IllegalArgumentException("Disable Waiting time < 1");
            }
            if (disableMaxRetry < 0) {
                throw new IllegalArgumentException("Disable Max retry < 1");
            }

            var objectMapper = ObjectMapperUtils.getPayloadObjectMapper();
            logger.info("Starting Message Bus for %s".formatted(bundleId));
            Connection connection = factory.newConnection(bundleId);
            logger.info("Connected to RabbitMQ @ %s".formatted(factory.getHost()));
            Channel channel = connection.createChannel();
            var nodeId = bundleId + "-" + Instant.now().toEpochMilli();
            channel.queueDeclare(nodeId, false, false, false, null);
            logger.info("Created service queue: %s".formatted(nodeId));

            channel.exchangeDeclare(exchange, BuiltinExchangeType.DIRECT, false, false, null);
            channel.queueBind(nodeId, exchange, nodeId);

            channel.exchangeDeclare(CLUSTER_BROADCAST_EXCHANGE, BuiltinExchangeType.FANOUT, false, false, null);
            channel.queueDeclare(CLUSTER_BROADCAST_QUEUE_PREFIX + nodeId, false, false, false, null);
            channel.queueBind(CLUSTER_BROADCAST_QUEUE_PREFIX + nodeId, CLUSTER_BROADCAST_EXCHANGE, "");
            logger.info("Created broadcast queue: %s".formatted(CLUSTER_BROADCAST_QUEUE_PREFIX + nodeId));

            var address = new RabbitMqNodeAddress(bundleId, bundleVersion, connection.getAddress().toString(), nodeId);
            channel.basicPublish(CLUSTER_BROADCAST_EXCHANGE, "", null,
                    RabbitMqMessage.create(address, SIGNAL_JOIN, objectMapper));
            logger.info("Cluster JOIN Sent");

            var mb =  new RabbitMqMessageBus(address, channel, exchange, requestTimeout, objectMapper, () -> {
                try {
                    channel.queueDelete(CLUSTER_BROADCAST_QUEUE_PREFIX + nodeId);
                    channel.queueDelete(nodeId);
                    channel.basicPublish(CLUSTER_BROADCAST_EXCHANGE, "", null,
                            RabbitMqMessage.create(address, SIGNAL_LEAVE, objectMapper));
                    channel.close();
                    connection.close();
                    logger.info("Cluster LEAVE Sent");
                } catch (IOException | TimeoutException ex) {
                    throw new RuntimeException(ex);
                }
            });
            if(disableWaitingTime > 0){
                mb.setDisableWaitingTime(disableWaitingTime);
            }
            if(disableMaxRetry > 0){
                mb.setDisableMaxRetry(disableMaxRetry);
            }
            return mb;
        }

        public String getBundleId() {
            return bundleId;
        }

        public Builder setBundleId(String bundleId) {
            this.bundleId = bundleId;
            return this;
        }

        public long getBundleVersion() {
            return bundleVersion;
        }

        public Builder setBundleVersion(long bundleVersion) {
            this.bundleVersion = bundleVersion;
            return this;
        }

        public String getExchange() {
            return exchange;
        }

        public Builder setExchange(String exchange) {
            this.exchange = exchange;
            return this;
        }

        public ConnectionFactory getFactory() {
            return factory;
        }

        public Builder setFactory(ConnectionFactory factory) {
            this.factory = factory;
            return this;
        }

        public int getRequestTimeout() {
            return requestTimeout;
        }

        public Builder setRequestTimeout(int requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public int getDisableWaitingTime() {
            return disableWaitingTime;
        }

        public Builder setDisableWaitingTime(int disableWaitingTime) {
            this.disableWaitingTime = disableWaitingTime;
            return this;
        }

        public int getDisableMaxRetry() {
            return disableMaxRetry;
        }

        public Builder setDisableMaxRetry(int disableMaxRetry) {
            this.disableMaxRetry = disableMaxRetry;
            return this;
        }


    }
}
