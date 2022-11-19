package org.eventrails.bus.rabbitmq;

import com.rabbitmq.client.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.messaging.bus.MessageNotReceivedException;
import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;

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
	private final Channel channel;
	private final RabbitMqNodeAddress address;
	private final String exchange;
	private final Runnable close;

	protected RabbitMqMessageBus(RabbitMqNodeAddress nodeAddress, Channel channel, String exchange, Runnable close) {
		super(subscriber -> {
			try
			{
				var view = new HashSet<NodeAddress>();
				view.add(nodeAddress);
				channel.basicConsume(nodeAddress.getNodeId(), true, (consumerTag, delivery) -> {
					channel.basicPublish("", delivery.getProperties().getReplyTo(),
							new AMQP.BasicProperties
									.Builder()
									.correlationId(delivery.getProperties().getCorrelationId())
									.build(), null);
					// channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
					try
					{
						var rabbitMessage = RabbitMqMessage.parse(delivery.getBody());
						subscriber.onMessage(rabbitMessage.getSource(), rabbitMessage.getMessage());
					} catch (Exception e)
					{
						logger.error(e);
					}

				}, consumerTag -> {
				});

				channel.basicConsume(CLUSTER_BROADCAST_QUEUE_PREFIX + nodeAddress.getNodeId(), true, (consumerTag, delivery) -> {
					try
					{
						var rabbitMessage = RabbitMqMessage.parse(delivery.getBody());
						if (rabbitMessage.getMessage() instanceof String)
						{
							String signal = rabbitMessage.getMessage().toString();
							logger.info("{} from {}", signal.toUpperCase(), rabbitMessage.getSourceNodeId());
							if (signal.equals("join"))
							{
								view.add(rabbitMessage.getSource());
								subscriber.onViewUpdate(view, Set.of(rabbitMessage.getSource()), Set.of());
								channel.basicPublish(CLUSTER_BROADCAST_EXCHANGE, "", null, RabbitMqMessage.create(nodeAddress, "hello"));
							}
							if (signal.equals("hello"))
							{
								if (!view.contains(rabbitMessage.getSource()))
								{
									view.add(rabbitMessage.getSource());
									subscriber.onViewUpdate(view, Set.of(rabbitMessage.getSource()), Set.of());
								}
							} else if (signal.equals("leave"))
							{
								view.remove(rabbitMessage.getSource());
								subscriber.onViewUpdate(view, Set.of(), Set.of(rabbitMessage.getSource()));
							}
						} else
						{
							subscriber.onMessage(rabbitMessage.getSource(), rabbitMessage.getMessage());
						}
					} catch (Exception e)
					{
						logger.error(e);
					}
				}, consumerTag -> {
				});
			} catch (IOException e)
			{
				throw new RuntimeException(e);
			}

		});
		this.close = close;
		this.channel = channel;
		this.address = nodeAddress;
		this.exchange = exchange;
	}

	public static MessageBus create(
			String bundleId,
			long bundleVersion,
			String exchange,
			String rabbitHost) throws Exception {
		logger.info("Starting Message Bus for %s".formatted(bundleId));
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(rabbitHost);
		Connection connection = factory.newConnection(bundleId);
		logger.info("Connected to RabbitMQ @ %s".formatted(rabbitHost));
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
		channel.basicPublish(CLUSTER_BROADCAST_EXCHANGE, "", null, RabbitMqMessage.create(address, "join"));
		logger.info("Cluster JOIN Sent");

		return new RabbitMqMessageBus(address, channel, exchange, () -> {
			try
			{
				channel.queueDelete(CLUSTER_BROADCAST_QUEUE_PREFIX + nodeId);
				channel.queueDelete(nodeId);
				channel.basicPublish(CLUSTER_BROADCAST_EXCHANGE, "", null, RabbitMqMessage.create(address, "leave"));
				channel.close();
				connection.close();
				System.out.println("Cluster LEAVE Sent");
				logger.info("Cluster LEAVE Sent");
			} catch (IOException | TimeoutException ex)
			{
				throw new RuntimeException(ex);
			}
		});
	}

	@Override
	public void cast(NodeAddress address, Serializable message) throws Exception {
		final String corrId = UUID.randomUUID().toString();
		String replyQueueName = channel.queueDeclare().getQueue();
		final CompletableFuture<Void> response = new CompletableFuture<>();
		AtomicReference<String> ctag = new AtomicReference<>();
		try
		{
			ctag.set(channel.basicConsume(replyQueueName, true, (consumerTag, delivery) -> {
				if (corrId.equals(delivery.getProperties().getCorrelationId()))
				{
					response.complete(null);
				}
			}, consumerTag -> {
			}));
		} catch (IOException e)
		{
			response.completeExceptionally(e);
		}
		channel.basicPublish(exchange, address.getNodeId(), new AMQP.BasicProperties.Builder()
						.replyTo(replyQueueName)
						.correlationId(corrId)
						.build(),
				RabbitMqMessage.create(this.address, message));
		try
		{
			response.get(120, TimeUnit.SECONDS);
		} catch (TimeoutException e)
		{
			channel.basicPublish(CLUSTER_BROADCAST_EXCHANGE, "", null, RabbitMqMessage.create(
					(RabbitMqNodeAddress) address, "leave"));
			throw new MessageNotReceivedException(address, message);
		} finally
		{
			channel.basicCancel(ctag.get());
		}
	}

	@Override
	public void broadcast(Serializable message) throws Exception {
		channel.basicPublish(CLUSTER_BROADCAST_EXCHANGE, "", null,
				RabbitMqMessage.create(this.address, message));
	}

	@Override
	public RabbitMqNodeAddress getAddress() {
		return address;
	}


	public void disconnect() {
		close.run();
	}
}
