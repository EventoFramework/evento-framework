package org.eventrails.server.service.messaging;

import org.eventrails.modeling.messaging.message.*;
import org.eventrails.modeling.messaging.message.bus.MessageBus;
import org.eventrails.modeling.messaging.message.bus.ResponseSender;
import org.eventrails.modeling.messaging.message.bus.ServerHandleInvocationMessage;
import org.eventrails.modeling.messaging.utils.RoundRobinAddressPicker;
import org.eventrails.modeling.state.SerializedAggregateState;
import org.eventrails.server.es.EventStore;
import org.eventrails.server.es.eventstore.EventStoreEntry;
import org.eventrails.server.service.BundleDeployService;
import org.eventrails.server.service.HandlerService;
import org.eventrails.server.service.performance.PerformanceService;
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

	private final RoundRobinAddressPicker roundRobinAddressPicker;

	private final BundleDeployService bundleDeployService;

	private final PerformanceService performanceService;


	public MessageGatewayService(
			HandlerService handlerService,
			LockRegistry lockRegistry,
			EventStore eventStore,
			MessageBus messageBus, BundleDeployService bundleDeployService, PerformanceService performanceService) {
		this.handlerService = handlerService;
		this.lockRegistry = lockRegistry;
		this.eventStore = eventStore;
		this.messageBus = messageBus;
		this.roundRobinAddressPicker = new RoundRobinAddressPicker(messageBus);
		this.bundleDeployService = bundleDeployService;
		this.performanceService = performanceService;
		messageBus.setRequestReceiver(this::messageHandler);
	}

	private void messageHandler(Serializable request, ResponseSender response) {


		var startTime = Instant.now();
		try
		{
			if (request instanceof DomainCommandMessage c)
			{

				var handler = handlerService.findByPayloadName(c.getCommandName());
				bundleDeployService.waitUntilAvailable(handler.getBundle().getName());

				var lock = lockRegistry.obtain("AGGREGATE:" + c.getAggregateId());
				lock.lock();
				var semaphore = new Semaphore(0);
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
							roundRobinAddressPicker.pickNodeAddress(handler.getBundle().getName()),
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
					performanceService.updatePerformances(handler, startTime);
				}

			} else if (request instanceof ServiceCommandMessage c)
			{
				var handler = handlerService.findByPayloadName(c.getCommandName());
				bundleDeployService.waitUntilAvailable(handler.getBundle().getName());
				messageBus.cast(
						roundRobinAddressPicker.pickNodeAddress(handler.getBundle().getName()),
						c,
						resp -> {
							if (resp != null)
								eventStore.publishEvent((EventMessage<?>) resp);
							response.sendResponse(resp);
							performanceService.updatePerformances(handler, startTime);
						},
						error -> {
							response.sendError(error.toThrowable());
							performanceService.updatePerformances(handler, startTime);
						}

				);

			} else if (request instanceof QueryMessage<?> q)
			{
				var handler = handlerService.findByPayloadName(q.getQueryName());
				bundleDeployService.waitUntilAvailable(handler.getBundle().getName());
				messageBus.cast(
						roundRobinAddressPicker.pickNodeAddress(handler.getBundle().getName()),
						q,
						resp -> {
							response.sendResponse(resp);
							performanceService.updatePerformances(handler, startTime);
						},
						error -> {
							response.sendError(error.toThrowable());
							performanceService.updatePerformances(handler, startTime);
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
		}
	}

}
