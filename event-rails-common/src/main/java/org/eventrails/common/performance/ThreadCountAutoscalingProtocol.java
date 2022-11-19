package org.eventrails.common.performance;

import org.eventrails.common.modeling.messaging.message.internal.ClusterNodeIsBoredMessage;
import org.eventrails.common.modeling.messaging.message.internal.ClusterNodeIsSufferingMessage;
import org.eventrails.common.messaging.bus.MessageBus;

public class ThreadCountAutoscalingProtocol implements AutoscalingProtocol {
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
					}catch (Exception e){
						e.printStackTrace();
					}
					bored = true;
				}
			}
		}
	}
}
