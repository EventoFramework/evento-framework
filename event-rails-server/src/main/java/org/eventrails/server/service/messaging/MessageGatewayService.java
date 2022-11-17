package org.eventrails.server.service.messaging;

import org.eventrails.common.modeling.messaging.message.application.*;
import org.eventrails.common.modeling.messaging.message.bus.ResponseSender;
import org.eventrails.common.modeling.messaging.message.internal.*;
import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.messaging.utils.AddressPicker;
import org.eventrails.common.messaging.utils.RoundRobinAddressPicker;
import org.eventrails.common.modeling.messaging.message.internal.discovery.ClusterNodeApplicationDiscoveryRequest;
import org.eventrails.common.modeling.messaging.message.internal.discovery.ClusterNodeApplicationDiscoveryResponse;
import org.eventrails.common.modeling.state.SerializedAggregateState;
import org.eventrails.server.es.EventStore;
import org.eventrails.server.es.eventstore.EventStoreEntry;
import org.eventrails.server.service.deploy.BundleDeployService;
import org.eventrails.server.service.HandlerService;
import org.eventrails.common.performance.ThreadCountAutoscalingProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Service
public class MessageGatewayService {
	private final HandlerService handlerService;

	private final LockRegistry lockRegistry;

	private final EventStore eventStore;

	private final MessageBus messageBus;

	private final AddressPicker<?> addressPicker;

	private final BundleDeployService bundleDeployService;

	private final ThreadCountAutoscalingProtocol threadCountAutoscalingProtocol;
	private final int minNodes;
	private final boolean autoscalingEnabled;
	private final String serverNodeName;

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
		this.serverNodeName = serverNodeName;
		this.eventStore = eventStore;
		this.messageBus = messageBus;
		this.addressPicker = new RoundRobinAddressPicker(messageBus);
		this.bundleDeployService = bundleDeployService;
		this.threadCountAutoscalingProtocol = new ThreadCountAutoscalingProtocol(
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
			this.threadCountAutoscalingProtocol.arrival();
			if (request instanceof DomainCommandMessage c)
			{

				System.out.println("START " + c.getClass().getSimpleName());
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
					System.out.println("INVOKE " + c.getClass().getSimpleName());
					messageBus.request(
							dest,
							invocation,
							resp -> {
								System.out.println("END " + c.getClass().getSimpleName());
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
				messageBus.request(
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
				messageBus.request(
						dest,
						q,
						response::sendResponse,
						error -> {
							response.sendError(error.toThrowable());
						}
				);

			} else if (request instanceof ClusterNodeApplicationDiscoveryRequest d){
				response.sendResponse(new ClusterNodeApplicationDiscoveryResponse(serverNodeName, new ArrayList<>()));
			}
			else
			{
				throw new IllegalArgumentException("Missing Handler " + ((ServerHandleInvocationMessage) request).getPayload());
			}
		} catch (Exception e)
		{
			e.printStackTrace();
			response.sendError(e);
		}finally
		{
			this.threadCountAutoscalingProtocol.departure();
		}
	}

}
