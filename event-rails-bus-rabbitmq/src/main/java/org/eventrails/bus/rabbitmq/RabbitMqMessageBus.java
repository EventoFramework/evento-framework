package org.eventrails.bus.rabbitmq;

import com.rabbitmq.client.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.common.serialization.ObjectMapperUtils;

import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class RabbitMqMessageBus extends MessageBus {

	private static final Logger logger = LogManager.getLogger(RabbitMqMessageBus.class);
	private static final String CLUSTER_BROADCAST_EXCHANGE = "cluster-broadcast";
	private static final String CLUSTER_BROADCAST_QUEUE_PREFIX = "broadcast-queue-for-";
	private final Channel channel;
	private final RabbitMqNodeAddress address;
	private final String exchange;

	protected RabbitMqMessageBus(RabbitMqNodeAddress nodeAddress, Channel channel, String exchange) {
		super(subscriber -> {
			try
			{
				var view = new HashSet<NodeAddress>();
				view.add(nodeAddress);
				channel.basicConsume(nodeAddress.getNodeId(), true, (consumerTag, delivery) -> {
					var rabbitMessage = RabbitMqMessage.parse(delivery.getBody());
					subscriber.onMessage(rabbitMessage.getSource(), rabbitMessage.getMessage());
				}, consumerTag -> {
				});

				channel.basicConsume(CLUSTER_BROADCAST_QUEUE_PREFIX + nodeAddress.getNodeId(), true, (consumerTag, delivery) -> {
					var rabbitMessage = RabbitMqMessage.parse(delivery.getBody());
					if (rabbitMessage.getMessage() instanceof String)
					{
						String signal = rabbitMessage.getMessage().toString();
						logger.info("{} from {}", signal.toUpperCase(), rabbitMessage.getSourceNodeId());
						if (signal.equals("join"))
						{
							view.add(rabbitMessage.getSource());
							subscriber.onViewUpdate(view);
							channel.basicPublish(CLUSTER_BROADCAST_EXCHANGE, "", null, RabbitMqMessage.create(nodeAddress, "hello"));
						}
						if (signal.equals("hello"))
						{
							view.add(rabbitMessage.getSource());
							subscriber.onViewUpdate(view);
						} else if (signal.equals("leave"))
						{
							view.remove(rabbitMessage.getSource());
							subscriber.onViewUpdate(view);
						}
					} else
					{
						subscriber.onMessage(rabbitMessage.getSource(), rabbitMessage.getMessage());
					}
				}, consumerTag -> {
				});
				subscriber.onViewUpdate(view);
			} catch (IOException e)
			{
				throw new RuntimeException(e);
			}
		});
		this.channel = channel;
		this.address = nodeAddress;
		this.exchange = exchange;
	}

	public static MessageBus create(
			String bundleName,
			String exchange,
			String rabbitHost) throws Exception {
		logger.info("Starting Message Bus for %s".formatted(bundleName));
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(rabbitHost);
		Connection connection = factory.newConnection(bundleName);
		logger.info("Connected to RabbitMQ @ %s".formatted(rabbitHost));
		Channel channel = connection.createChannel();
		var nodeId = bundleName + "-" + Instant.now().toEpochMilli();
		channel.queueDeclare(nodeId, false, false, false, null);
		logger.info("Created service queue: %s".formatted(nodeId));
		channel.exchangeDeclare(exchange, BuiltinExchangeType.DIRECT, false, false, null);
		channel.queueBind(nodeId,exchange, nodeId);

		channel.exchangeDeclare(CLUSTER_BROADCAST_EXCHANGE, BuiltinExchangeType.FANOUT, false, false, null);
		channel.queueDeclare(CLUSTER_BROADCAST_QUEUE_PREFIX + nodeId, false, false, false, null);
		channel.queueBind(CLUSTER_BROADCAST_QUEUE_PREFIX + nodeId, CLUSTER_BROADCAST_EXCHANGE, "");
		logger.info("Created cluster queue: %s".formatted(CLUSTER_BROADCAST_QUEUE_PREFIX + nodeId));

		var address = new RabbitMqNodeAddress(bundleName, connection.getAddress().toString(), nodeId);
		channel.basicPublish(CLUSTER_BROADCAST_EXCHANGE, "", null, RabbitMqMessage.create(address, "join"));
		logger.info("Cluster JOIN Sent");

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try
			{
				channel.basicPublish(CLUSTER_BROADCAST_EXCHANGE, "", null, RabbitMqMessage.create(address, "leave"));
				channel.queueDelete(CLUSTER_BROADCAST_QUEUE_PREFIX + nodeId);
				channel.queueDelete(nodeId);
				logger.info("Cluster LEAVE Sent");
			} catch (IOException ex)
			{
				throw new RuntimeException(ex);
			}
		}));

		return new RabbitMqMessageBus(address, channel, exchange);
	}

	@Override
	public void cast(NodeAddress address, Serializable message) throws Exception {
		channel.basicPublish(exchange, address.getNodeId(), null,
				RabbitMqMessage.create(this.address, message));
	}

	@Override
	public void broadcast(Serializable message) throws Exception {
		channel.basicPublish(CLUSTER_BROADCAST_EXCHANGE, "", null,
				RabbitMqMessage.create(this.address, message));
	}

	@Override
	public NodeAddress getAddress() {
		return address;
	}
}
