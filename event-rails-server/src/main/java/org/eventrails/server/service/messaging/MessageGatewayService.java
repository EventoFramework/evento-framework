package org.eventrails.server.service.messaging;

import org.eventrails.modeling.messaging.message.*;
import org.eventrails.modeling.messaging.message.bus.*;
import org.eventrails.modeling.messaging.utils.AddressPicker;
import org.eventrails.modeling.messaging.utils.RoundRobinAddressPicker;
import org.eventrails.modeling.state.SerializedAggregateState;
import org.eventrails.server.es.EventStore;
import org.eventrails.server.es.eventstore.EventStoreEntry;
import org.eventrails.server.service.BundleDeployService;
import org.eventrails.server.service.HandlerService;
import org.eventrails.shared.messaging.AutoscalingProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Service
public class MessageGatewayService {
	private final HandlerService handlerService;

	private final LockRegistry lockRegistry;

	private final EventStore eventStore;

	private final MessageBus messageBus;

	private final AddressPicker addressPicker;

	private final BundleDeployService bundleDeployService;

	private final AutoscalingProtocol autoscalingProtocol;
	private final int minNodes;
	private final boolean autoscalingEnabled;

	public MessageGatewayService(
			HandlerService handlerService,
			LockRegistry lockRegistry,
			EventStore eventStore,
			MessageBus messageBus,
			BundleDeployService bundleDeployService,
			@Value("${eventrails.cluster.node.server.name}") String serverNodeName,
			@Value("${eventrails.cluster.autoscaling.max.threads}") int maxThreads,
			@Value("${eventrails.cluster.autoscaling.max.overflow}") int maxOverflow,
			@Value("${eventrails.cluster.autoscaling.min.threads}") int minThreads,
			@Value("${eventrails.cluster.autoscaling.max.underflow}") int maxUnderflow,
			@Value("${eventrails.cluster.autoscaling.min.nodes}") int minNodes,
			@Value("${eventrails.cluster.autoscaling.enabled}") boolean autoscalingEnabled
			) {
		this.handlerService = handlerService;
		this.lockRegistry = lockRegistry;
		this.eventStore = eventStore;
		this.messageBus = messageBus;
		this.addressPicker = new RoundRobinAddressPicker(messageBus);
		this.bundleDeployService = bundleDeployService;
		this.autoscalingProtocol = new AutoscalingProtocol(
				serverNodeName,
				serverNodeName,
				messageBus,
				maxThreads,
				minThreads,
				maxOverflow,
				maxUnderflow
		);
		this.minNodes = minNodes;
		this.autoscalingEnabled = autoscalingEnabled;
		messageBus.setRequestReceiver(this::requestHandler);
		messageBus.setMessageReceiver(this::messageHandler);
	}

	private void messageHandler(Serializable request) {
		try
		{
			if(this.autoscalingEnabled)
			{
				if (request instanceof ClusterNodeIsSufferingMessage m)
				{
					bundleDeployService.spawn(m.getBundleName());
				} else if (request instanceof ClusterNodeIsBoredMessage m)
				{
					var nodes = messageBus.findAllNodeAddresses(m.getBundleName());
					if (nodes.size() > minNodes)
					{
						messageBus.sendKill(m.getNodeId());
					}
				}
			}
		}catch (Exception e){
			e.printStackTrace();
		}
	}

	private void requestHandler(Serializable request, ResponseSender response) {
		try
		{
			this.autoscalingProtocol.arrival();
			if (request instanceof DomainCommandMessage c)
			{

				var handler = handlerService.findByPayloadName(c.getCommandName());
				bundleDeployService.waitUntilAvailable(handler.getBundle().getName());

				var lock = lockRegistry.obtain("AGGREGATE:" + c.getAggregateId());
				lock.lock();
				var semaphore = new Semaphore(0);
				var dest = addressPicker.pickNodeAddress(handler.getBundle().getName());
				try
				{
					var invocation = new DecoratedDomainCommandMessage();
					invocation.setCommandMessage(c);

					var snapshot = eventStore.fetchSnapshot(c.getAggregateId());
					if (snapshot == null)
					{
						invocation.setEventStream(eventStore.fetchAggregateStory(c.getAggregateId())
								.stream().map(EventStoreEntry::getEventMessage)
								.map(e -> ((DomainEventMessage) e)).collect(Collectors.toList()));
						invocation.setSerializedAggregateState(new SerializedAggregateState<>(null));
					} else
					{
						invocation.setEventStream(eventStore.fetchAggregateStory(c.getAggregateId(),
										snapshot.getAggregateSequenceNumber())
								.stream().map(EventStoreEntry::getEventMessage)
								.map(e -> ((DomainEventMessage) e)).collect(Collectors.toList()));
						invocation.setSerializedAggregateState(snapshot.getAggregateState());
					}
					messageBus.cast(
							dest,
							invocation,
							resp -> {
								var cr = (DomainCommandResponseMessage) resp;
								var aggregateSequenceNumber = eventStore.publishEvent(cr.getDomainEventMessage(), c.getAggregateId());
								if(cr.getSerializedAggregateState() != null){
									eventStore.saveSnapshot(
											c.getAggregateId(),
											aggregateSequenceNumber,
											cr.getSerializedAggregateState()
									);
								}
								response.sendResponse(resp);
								semaphore.release();
							},
							error -> {
								response.sendError(error.toThrowable());
								semaphore.release();
							}

					);
				}catch (Exception e){
					semaphore.release();
					throw e;
				}finally
				{
					semaphore.acquire();
					lock.unlock();
				}

			} else if (request instanceof ServiceCommandMessage c)
			{
				var handler = handlerService.findByPayloadName(c.getCommandName());
				bundleDeployService.waitUntilAvailable(handler.getBundle().getName());
				var dest = addressPicker.pickNodeAddress(handler.getBundle().getName());
				messageBus.cast(
						dest,
						c,
						resp -> {
							if (resp != null)
								eventStore.publishEvent((EventMessage<?>) resp);
							response.sendResponse(resp);
						},
						error -> {
							response.sendError(error.toThrowable());
						}

				);

			} else if (request instanceof QueryMessage<?> q)
			{
				var handler = handlerService.findByPayloadName(q.getQueryName());
				bundleDeployService.waitUntilAvailable(handler.getBundle().getName());
				var dest = addressPicker.pickNodeAddress(handler.getBundle().getName());
				messageBus.cast(
						dest,
						q,
						response::sendResponse,
						error -> {
							response.sendError(error.toThrowable());
						}
				);

			} else
			{
				throw new IllegalArgumentException("Missing Handler " + ((ServerHandleInvocationMessage) request).getPayload());
			}
		} catch (Exception e)
		{
			e.printStackTrace();
			response.sendError(e);
		}finally
		{
			this.autoscalingProtocol.departure();
		}
	}

}
