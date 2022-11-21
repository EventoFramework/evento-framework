package org.eventrails.server.service.messaging;

import org.eventrails.common.modeling.messaging.message.application.*;
import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.common.modeling.messaging.message.bus.ResponseSender;
import org.eventrails.common.modeling.messaging.message.internal.*;
import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.messaging.utils.AddressPicker;
import org.eventrails.common.messaging.utils.RoundRobinAddressPicker;
import org.eventrails.common.modeling.messaging.message.internal.discovery.ClusterNodeApplicationDiscoveryRequest;
import org.eventrails.common.modeling.messaging.message.internal.discovery.ClusterNodeApplicationDiscoveryResponse;
import org.eventrails.common.modeling.messaging.message.internal.processor.FetchEventRequest;
import org.eventrails.common.modeling.state.SerializedAggregateState;
import org.eventrails.server.es.EventStore;
import org.eventrails.server.es.eventstore.EventStoreEntry;
import org.eventrails.server.service.deploy.BundleDeployService;
import org.eventrails.server.service.HandlerService;
import org.eventrails.common.performance.ThreadCountAutoscalingProtocol;
import org.eventrails.server.service.performance.PerformanceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import static org.eventrails.server.service.performance.PerformanceService.*;

@Service
public class MessageGatewayService {
	private static final String AGGREGATE_LOCK_PREFIX = "AGGREGATE:";
	private final HandlerService handlerService;

	private final LockRegistry lockRegistry;

	private final EventStore eventStore;

	private final MessageBus messageBus;

	private final AddressPicker<?> addressPicker;

	private final BundleDeployService bundleDeployService;

	private final ThreadCountAutoscalingProtocol threadCountAutoscalingProtocol;
	private final int minNodes;
	private final boolean autoscalingEnabled;
	private final String serverId;
	private final long serverVersion;

	private final PerformanceService performanceService;
	private final EventDispatcher eventDispatcher;

	public MessageGatewayService(
			HandlerService handlerService,
			LockRegistry lockRegistry,
			EventStore eventStore,
			MessageBus messageBus,
			BundleDeployService bundleDeployService,
			@Value("${eventrails.cluster.node.server.id}") String serverId,
			@Value("${eventrails.cluster.node.server.version}") long serverVersion,
			@Value("${eventrails.cluster.autoscaling.max.threads}") int maxThreads,
			@Value("${eventrails.cluster.autoscaling.max.overflow}") int maxOverflow,
			@Value("${eventrails.cluster.autoscaling.min.threads}") int minThreads,
			@Value("${eventrails.cluster.autoscaling.max.underflow}") int maxUnderflow,
			@Value("${eventrails.cluster.autoscaling.min.nodes}") int minNodes,
			@Value("${eventrails.cluster.autoscaling.enabled}") boolean autoscalingEnabled,
			PerformanceService performanceService,
			EventDispatcher eventDispatcher) {
		this.handlerService = handlerService;
		this.lockRegistry = lockRegistry;
		this.serverId = serverId;
		this.serverVersion = serverVersion;
		this.eventStore = eventStore;
		this.messageBus = messageBus;
		this.eventDispatcher = eventDispatcher;
		this.addressPicker = new RoundRobinAddressPicker(messageBus);
		this.bundleDeployService = bundleDeployService;
		this.performanceService = performanceService;

		this.threadCountAutoscalingProtocol = new ThreadCountAutoscalingProtocol(
				this.serverId,
				this.serverId,
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

	private void messageHandler(NodeAddress source, Serializable request) {
		try
		{
			if (this.autoscalingEnabled)
			{
				if (request instanceof ClusterNodeIsSufferingMessage m)
				{
					bundleDeployService.spawn(m.getBundleId());
				} else if (request instanceof ClusterNodeIsBoredMessage m)
				{
					var nodes = messageBus.findAllNodeAddresses(m.getBundleId());
					if (nodes.size() > minNodes)
					{
						messageBus.sendKill(m.getNodeId());
					}
				}
			}
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private void requestHandler(NodeAddress source, Serializable request, ResponseSender response) {
		try
		{
			this.threadCountAutoscalingProtocol.arrival();
			if (request instanceof DomainCommandMessage c)
			{
				var handler = handlerService.findByPayloadName(c.getCommandName());
				bundleDeployService.waitUntilAvailable(handler.getBundle().getId());
				var start = PerformanceService.now();

				var lock = lockRegistry.obtain(AGGREGATE_LOCK_PREFIX + c.getAggregateId());
				lock.lock();
				var semaphore = new Semaphore(0);

				var dest = addressPicker.pickNodeAddress(handler.getBundle().getId());

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
					performanceService.updatePerformances(
							SERVER,
							GATEWAY_COMPONENT,
							c.getCommandName(),
							start
					);
					var invocationStart = PerformanceService.now();
					messageBus.request(
							dest,
							invocation,
							resp -> {
								performanceService.updatePerformances(
										dest.getBundleId(),
										handler.getComponentName(),
										c.getCommandName(),
										invocationStart
								);

								var cr = (DomainCommandResponseMessage) resp;
								var esStoreStart = PerformanceService.now();
								var aggregateSequenceNumber = eventStore.publishEvent(cr.getDomainEventMessage(), c.getAggregateId());
								if (cr.getSerializedAggregateState() != null)
								{
									eventStore.saveSnapshot(
											c.getAggregateId(),
											aggregateSequenceNumber,
											cr.getSerializedAggregateState()
									);
								}
								performanceService.updatePerformances(
										EVENT_STORE,
										EVENT_STORE_COMPONENT,
										cr.getDomainEventMessage().getEventName(),
										esStoreStart
								);
								response.sendResponse(resp);
								semaphore.release();

							},
							error -> {
								performanceService.updatePerformances(
										dest.getBundleId(),
										handler.getComponentName(),
										c.getCommandName(),
										invocationStart
								);
								response.sendError(error.toThrowable());
								semaphore.release();

							}

					);
				} catch (Exception e)
				{
					semaphore.release();
					throw e;
				} finally
				{
					semaphore.acquire();
					lock.unlock();
				}

			} else if (request instanceof ServiceCommandMessage c)
			{
				var handler = handlerService.findByPayloadName(c.getCommandName());
				bundleDeployService.waitUntilAvailable(handler.getBundle().getId());
				var dest = addressPicker.pickNodeAddress(handler.getBundle().getId());
				var invocationStart = PerformanceService.now();
				messageBus.request(
						dest,
						c,
						resp -> {
							performanceService.updatePerformances(
									dest.getBundleId(),
									handler.getComponentName(),
									c.getCommandName(),
									invocationStart
							);
							if (resp != null && ((EventMessage<?>) resp).getType() != null)
							{
								var esStoreStart = PerformanceService.now();
								eventStore.publishEvent((EventMessage<?>) resp);
								performanceService.updatePerformances(
										EVENT_STORE,
										EVENT_STORE_COMPONENT,
										((EventMessage<?>) resp).getEventName(),
										esStoreStart
								);
							}
							response.sendResponse(resp);

						},
						error -> {
							response.sendError(error.toThrowable());
							performanceService.updatePerformances(
									dest.getBundleId(),
									handler.getComponentName(),
									c.getCommandName(),
									invocationStart
							);
						}

				);

			} else if (request instanceof QueryMessage<?> q)
			{
				var handler = handlerService.findByPayloadName(q.getQueryName());
				bundleDeployService.waitUntilAvailable(handler.getBundle().getId());
				var dest = addressPicker.pickNodeAddress(handler.getBundle().getId());
				var invocationStart = PerformanceService.now();
				messageBus.request(
						dest,
						q,
						resp -> {
							performanceService.updatePerformances(
									dest.getBundleId(),
									handler.getComponentName(),
									q.getQueryName(),
									invocationStart
							);
							response.sendResponse(resp);
						},
						error -> {
							performanceService.updatePerformances(
									dest.getBundleId(),
									handler.getComponentName(),
									q.getQueryName(),
									invocationStart
							);
							response.sendError(error.toThrowable());
						}
				);

			} else if (request instanceof ClusterNodeApplicationDiscoveryRequest d)
			{
				response.sendResponse(new ClusterNodeApplicationDiscoveryResponse(serverId, serverVersion, new ArrayList<>()));
			} else if (request instanceof FetchEventRequest d){
				eventDispatcher.handleRequest(source, d, response);
			}
			else
			{
				throw new IllegalArgumentException("Missing Handler " + ((ServerHandleInvocationMessage) request).getPayload());
			}
		} catch (Exception e)
		{
			e.printStackTrace();
			response.sendError(e);
		} finally
		{
			this.threadCountAutoscalingProtocol.departure();
		}
	}

}
