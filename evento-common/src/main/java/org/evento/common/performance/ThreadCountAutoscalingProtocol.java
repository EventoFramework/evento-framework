package org.evento.common.performance;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.modeling.messaging.message.internal.ClusterNodeIsBoredMessage;
import org.evento.common.modeling.messaging.message.internal.ClusterNodeIsSufferingMessage;
import org.evento.common.messaging.bus.MessageBus;

public class ThreadCountAutoscalingProtocol implements AutoscalingProtocol {

	private final Logger logger = LogManager.getLogger(ThreadCountAutoscalingProtocol.class);
	private final int maxThreadCount;
	private final int minThreadCount;
	private final int maxOverflowCount;

	private final int maxUnderflowCount;

	private final MessageBus messageBus;

	private final String bundleId;
	private final String serverName;

	private int threadCount = 0;
	private int overflowCount = 0;
	private int underflowCount = 0;
	private boolean suffering = false;
	private boolean bored = true;

	public ThreadCountAutoscalingProtocol(
			String bundleId,
			String serverName,
			MessageBus messageBus,
			int maxThreadCount,
			int minThreadCount,
			int maxOverflowCount,
			int maxUnderflowCount) {
		this.bundleId = bundleId;
		this.serverName = serverName;
		this.messageBus = messageBus;
		this.maxUnderflowCount = maxUnderflowCount;
		this.minThreadCount = minThreadCount;
		this.maxThreadCount = maxThreadCount;
		this.maxOverflowCount = maxOverflowCount;
	}

	@Override
	public synchronized void arrival() {
		if(++threadCount >= minThreadCount)
		{
			underflowCount = 0;
			bored = false;
			if (threadCount > maxThreadCount)
			{
				if (++overflowCount >= maxOverflowCount && !suffering)
				{
					try
					{
						messageBus.cast(
								messageBus.findNodeAddress(serverName),
								new ClusterNodeIsSufferingMessage(bundleId)
						);
						logger.info("ClusterNodeIsSufferingMessage sent");
					} catch (Exception e)
					{
						e.printStackTrace();
					}
					suffering = true;
				}
			}
		}
	}

	@Override
	public synchronized void departure() {
		if(--threadCount <= maxThreadCount){
			overflowCount = 0;
			suffering = false;
			if(threadCount < minThreadCount){
				if(++underflowCount >= maxUnderflowCount && !bored){
					try
					{
						messageBus.cast(
								messageBus.findNodeAddress(serverName),
								new ClusterNodeIsBoredMessage(bundleId, messageBus.getAddress().getNodeId())
						);
						logger.info("ClusterNodeIsBoredMessage sent");
					}catch (Exception e){
						e.printStackTrace();
					}
					bored = true;
				}
			}
		}
	}
}
