package org.evento.common.performance;

import org.evento.common.messaging.bus.MessageBus;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class PerformanceService {

	public static final String EVENT_STORE = "event-store";
	public static final String EVENT_STORE_COMPONENT = "EventStore";
	public static final String GATEWAY_COMPONENT = "Gateway";
	public static final String SERVER = "server";
	private final MessageBus messageBus;
	private final String serverNodeName;

	private final Random random = new Random();
	private static double performanceRate = 1;

	public PerformanceService(MessageBus messageBus,
							  String serverNodeName) {
		this.messageBus = messageBus;
		this.serverNodeName = serverNodeName;
	}

	public void sendPerformances(String bundle, String component, String action, Instant startTime) {
		if (random.nextDouble(0.0, 1.0) > performanceRate) return;
		try
		{
			messageBus.cast(messageBus.findNodeAddress(serverNodeName),
					new PerformanceServiceTimeMessage(bundle, component, action, Instant.now().toEpochMilli() - startTime.toEpochMilli()));
		} catch (Exception ignored)
		{

		}
	}

	public static void setPerformanceRate(double performanceRate) {
		PerformanceService.performanceRate = performanceRate;
	}


	public void sendInvocations(String bundle, String component, String action, HashMap<String, AtomicInteger> invocationCounter) {
		if (random.nextDouble(0.0, 1.0) > performanceRate) return;
		try
		{

			var invocations = new HashMap<String, Integer>();
			for (Map.Entry<String, AtomicInteger> e : invocationCounter.entrySet())
			{
				invocations.put(e.getKey(), e.getValue().get());
			}
			messageBus.cast(messageBus.findNodeAddress(serverNodeName),
					new PerformanceInvocationsMessage(bundle, component, action, invocations));

		} catch (Exception ignored)
		{

		}
	}
}
