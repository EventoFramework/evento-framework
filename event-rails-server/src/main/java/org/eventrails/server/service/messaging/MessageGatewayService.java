package org.eventrails.server.service.messaging;

import org.eventrails.modeling.messaging.message.DecoratedDomainCommandMessage;
import org.eventrails.modeling.messaging.message.*;
import org.eventrails.modeling.messaging.message.bus.*;
import org.eventrails.modeling.messaging.utils.RoundRobinAddressPicker;
import org.eventrails.modeling.state.SerializedAggregateState;
import org.eventrails.server.es.EventStore;
import org.eventrails.server.es.eventstore.EventStoreEntry;
import org.eventrails.server.service.HandlerService;
import org.eventrails.server.service.RanchDeployService;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

@Service
public class MessageGatewayService {
	private final HandlerService handlerService;

	private final LockRegistry lockRegistry;

	private final EventStore eventStore;

	private final MessageBus messageBus;

	private final RoundRobinAddressPicker roundRobinAddressPicker;

	private final RanchDeployService ranchDeployService;


	public MessageGatewayService(
			HandlerService handlerService,
			LockRegistry lockRegistry,
			EventStore eventStore,
			MessageBus messageBus, RanchDeployService ranchDeployService) {
		this.handlerService = handlerService;
		this.lockRegistry = lockRegistry;
		this.eventStore = eventStore;
		this.messageBus = messageBus;
		this.roundRobinAddressPicker = new RoundRobinAddressPicker(messageBus);
		this.ranchDeployService = ranchDeployService;
		messageBus.setRequestReceiver(this::messageHandler);
	}

	private void messageHandler(Serializable request, ResponseSender response) {

		try
		{
			if (request instanceof DomainCommandMessage c)
			{

				var handler = handlerService.findByPayloadName(c.getCommandName());
				ranchDeployService.waitUntilAvailable(handler.getRanch().getName());

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
							roundRobinAddressPicker.pickNodeAddress(handler.getRanch().getName()),
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
				ranchDeployService.waitUntilAvailable(handler.getRanch().getName());
				messageBus.cast(
						roundRobinAddressPicker.pickNodeAddress(handler.getRanch().getName()),
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
				ranchDeployService.waitUntilAvailable(handler.getRanch().getName());
				messageBus.cast(
						roundRobinAddressPicker.pickNodeAddress(handler.getRanch().getName()),
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
		}
	}

}
