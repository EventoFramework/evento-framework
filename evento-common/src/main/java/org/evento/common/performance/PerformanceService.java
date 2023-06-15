package org.evento.common.performance;

import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.modeling.messaging.message.application.Message;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class PerformanceService {


	public static final String EVENTO_TRACING_ID_METADATA = "evento.tracing.id";

	public static final String EVENT_STORE = "event-store";
	public static final String EVENT_STORE_COMPONENT = "EventStore";
	public static final String GATEWAY_COMPONENT = "Gateway";
	public static final String SERVER = "server";
	private final MessageBus messageBus;
	private final String serverNodeName;

	private final Random random = new Random();
	private double performanceRate = 1;

	public PerformanceService(MessageBus messageBus,
							  String serverNodeName) {
		this.messageBus = messageBus;
		this.serverNodeName = serverNodeName;
	}

	public PerformanceService(MessageBus messageBus,
							  String serverNodeName,
							  double rate) {
		this(messageBus, serverNodeName);
		performanceRate = rate;
	}

	public final void sendServiceTimeMetric(String bundle, String component, Message<?> message, Instant startTime) {
		var trace = message.getMetadata() == null ? null : message.getMetadata().getOrDefault(EVENTO_TRACING_ID_METADATA, null);
		sendServiceTimeMetric(bundle, component, message.getPayloadName(), startTime, trace);
	}

	public final void sendServiceTimeMetric(String bundle, String component, String action, Instant startTime,
											String trace) {
		if (random.nextDouble(0.0, 1.0) > performanceRate) return;
		try
		{
			var st = new PerformanceServiceTimeMessage(bundle, component, action, Instant.now().toEpochMilli() - startTime.toEpochMilli());
			messageBus.cast(messageBus.findNodeAddress(serverNodeName),
					st);
			onServiceTimeSent(st);
		} catch (Exception ignored)
		{

		}
	}

	public void onServiceTimeSent(PerformanceServiceTimeMessage performanceServiceTimeMessage){

	}

	public void setPerformanceRate(double performanceRate) {
		this.performanceRate = performanceRate;
	}


	public final void sendInvocationsMetric(String bundle, String component, String action,
											HashMap<String, AtomicInteger> invocationCounter, String trace) {
		if (random.nextDouble(0.0, 1.0) > performanceRate) return;
		try
		{

			var invocations = new HashMap<String, Integer>();
			for (Map.Entry<String, AtomicInteger> e : invocationCounter.entrySet())
			{
				invocations.put(e.getKey(), e.getValue().get());
			}
			var pi = new PerformanceInvocationsMessage(bundle, component, action, invocations);
			messageBus.cast(messageBus.findNodeAddress(serverNodeName),
					pi);
			onInvocationSent(pi);

		} catch (Exception ignored)
		{

		}
	}

	public void onInvocationSent(PerformanceInvocationsMessage performanceInvocationsMessage){

	}
}
