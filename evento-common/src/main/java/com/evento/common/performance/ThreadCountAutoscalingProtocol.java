package com.evento.common.performance;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.utils.Sleep;

import java.time.Instant;

/**
 * The ThreadCountAutoscalingProtocol class is a concrete implementation of the AutoscalingProtocol abstract class.
 * It provides an autoscaling protocol that adjusts the thread count based on the arrival and departure of requests or messages.
 */
public class ThreadCountAutoscalingProtocol extends AutoscalingProtocol {

	private static final Logger logger = LogManager.getLogger(ThreadCountAutoscalingProtocol.class);
	private final int maxThreadCount;
	private final int minThreadCount;
	private final int maxOverflowCount;

	private final int maxUnderflowCount;

    private int threadCount = 0;
	private int overflowCount = 0;
	private int underflowCount = 0;
	private boolean suffering = false;
	private boolean bored = true;

	private long lastDepartureAt = 0;
	private long lastDepartureCheck = 0;
	private long boredSentDepartureTime = -1;

	/**
	 * The ThreadCountAutoscalingProtocol class is a concrete implementation of the AutoscalingProtocol abstract class.
	 * It provides an autoscaling protocol that adjusts the thread count based on the arrival and departure of requests or messages.
     * @param eventoServer an evento server connection instance
     * @param maxThreadCount maximum thread concurrently available
     * @param minThreadCount minimum thread active to handle messages
     * @param maxOverflowCount overflow to detect suffering
     * @param maxUnderflowCount underflow to detect boredom
     * @param boredTimeout interval to detect boredom
     */
	public ThreadCountAutoscalingProtocol(
			EventoServer eventoServer,
			int maxThreadCount,
			int minThreadCount,
			int maxOverflowCount,
			int maxUnderflowCount, long boredTimeout) {
		super(eventoServer);
		this.maxUnderflowCount = maxUnderflowCount;
		this.minThreadCount = minThreadCount;
		this.maxThreadCount = maxThreadCount;
		this.maxOverflowCount = maxOverflowCount;

        var t = new Thread(() -> {
			while (true)
			{
				try
				{
					Sleep.apply(boredTimeout);
					if (threadCount == 0 && lastDepartureAt == lastDepartureCheck && boredSentDepartureTime != lastDepartureAt)
					{
						overflowCount = 0;
						suffering = false;
						bored = true;
						sendBoredSignal();
						logger.info("ClusterNodeIsSufferingMessage sent! (Timeout)");
						boredSentDepartureTime = lastDepartureAt;
					}
					lastDepartureCheck = lastDepartureAt;
				} catch (Exception e)
				{
					throw new RuntimeException(e);
				}
			}
		});
		t.setName("ThreadCountAutoscalingProtocol");
		t.start();
	}

	@Override
	public synchronized void arrival() {
		if (++threadCount >= minThreadCount)
		{
			underflowCount = 0;
			bored = false;
			if (threadCount > maxThreadCount)
			{
				if (++overflowCount >= maxOverflowCount && !suffering)
				{
					try
					{
						sendSufferingSignal();
						logger.info("ClusterNodeIsSufferingMessage sent!");
					} catch (Exception e)
					{
						logger.error("Error sending suffering signal", e);
					}
					suffering = true;
				}
			}
		}
		logger.trace("ARRIVAL: {}", threadCount);

	}

	@Override
	public synchronized void departure() {
		if (--threadCount <= maxThreadCount)
		{
			overflowCount = 0;
			suffering = false;
			if (threadCount < minThreadCount)
			{
				if (++underflowCount >= maxUnderflowCount && !bored)
				{
					try
					{
						sendBoredSignal();
						logger.info("ClusterNodeIsBoredMessage sent!");
					} catch (Exception e)
					{
						logger.error("Error sending bored signal", e);
					}
					bored = true;
				}
			}
		}
		lastDepartureAt = Instant.now().toEpochMilli();
		logger.trace("DEPARTURE: {}", threadCount);

	}
}
