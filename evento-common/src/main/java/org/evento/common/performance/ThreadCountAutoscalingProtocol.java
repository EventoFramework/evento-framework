package org.evento.common.performance;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.messaging.bus.EventoServer;
import org.evento.common.utils.Sleep;

import java.time.Instant;

public class ThreadCountAutoscalingProtocol extends AutoscalingProtocol {

	private final Logger logger = LogManager.getLogger(ThreadCountAutoscalingProtocol.class);
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

        new Thread(() -> {
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
						boredSentDepartureTime = lastDepartureAt;
					}
					lastDepartureCheck = lastDepartureAt;
				} catch (Exception e)
				{
					throw new RuntimeException(e);
				}
			}
		}).start();
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
					} catch (Exception e)
					{
						logger.error("Error sending suffering signal", e);
					}
					suffering = true;
				}
			}
		}
		logger.debug("ARRIVAL: " + threadCount);

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
					} catch (Exception e)
					{
						logger.error("Error sending bored signal", e);
					}
					bored = true;
				}
			}
		}
		lastDepartureAt = Instant.now().toEpochMilli();
		logger.debug("DEPARTURE: " + threadCount);

	}
}
